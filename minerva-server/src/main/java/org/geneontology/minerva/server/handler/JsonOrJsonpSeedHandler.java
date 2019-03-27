package org.geneontology.minerva.server.handler;

import static org.geneontology.minerva.server.handler.OperationsTools.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.generate.GolrSeedingDataProvider;
import org.geneontology.minerva.generate.ModelSeeding;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.server.handler.M3SeedHandler.SeedResponse.SeedResponseData;
import org.geneontology.reasoner.ExpressionMaterializingReasoner;
import org.geneontology.reasoner.ExpressionMaterializingReasonerFactory;
import org.geneontology.reasoner.OWLExtendedReasonerFactory;
import org.glassfish.jersey.server.JSONP;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;

import com.google.common.reflect.TypeToken;

import owltools.gaf.eco.SimpleEcoMapper;
import owltools.graph.OWLGraphWrapper;

public class JsonOrJsonpSeedHandler extends ModelCreator implements M3SeedHandler {

	public static final String JSONP_DEFAULT_CALLBACK = "jsonp";
	public static final String JSONP_DEFAULT_OVERWRITE = "json.wrf";
	
	private static final Logger logger = Logger.getLogger(JsonOrJsonpSeedHandler.class);
	
	private final String golrUrl;
	private final SimpleEcoMapper ecoMapper;
	private final OWLExtendedReasonerFactory<ExpressionMaterializingReasoner> factory;
	
	private final Type requestType = new TypeToken<SeedRequest[]>(){

		// generated
		private static final long serialVersionUID = 5452629810143143422L;
		
	}.getType();
	
	public JsonOrJsonpSeedHandler(UndoAwareMolecularModelManager m3, String defaultModelState, String golr, SimpleEcoMapper ecoMapper) {
		super(m3, defaultModelState);
		this.golrUrl = golr;
		this.ecoMapper = ecoMapper;
		factory = new ExpressionMaterializingReasonerFactory(new ElkReasonerFactory());
		System.out.println("JsonOrJsonpSeedHandler instanciated with " + defaultModelState + ", " + golr + ", " + ecoMapper);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public SeedResponse fromProcessPost(String intention, String packetId, String requestString) {
		// only privileged calls are allowed
		SeedResponse response = new SeedResponse(null, Collections.emptySet(), intention, packetId);
		return error(response, "Insufficient permissions for seed operation.", null);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public SeedResponse fromProcessPostPrivileged(String uid, Set<String> providerGroups, String intention, String packetId, String requestString) {
		return fromProcess(uid, providerGroups, intention, checkPacketId(packetId), requestString);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public SeedResponse fromProcessGet(String intention, String packetId, String requestString) {
		// only privileged calls are allowed
		SeedResponse response = new SeedResponse(null, Collections.emptySet(), intention, packetId);
		return error(response, "Insufficient permissions for seed operation.", null);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public SeedResponse fromProcessGetPrivileged(String uid, Set<String> providerGroups, String intention, String packetId, String requestString) {
		return fromProcess(uid, providerGroups, intention, checkPacketId(packetId), requestString);
	}

	private static String checkPacketId(String packetId) {
		if (packetId == null) {
			packetId = PacketIdGenerator.generateId();
		}
		return packetId;
	}
	
	private SeedResponse fromProcess(String uid, Set<String> providerGroups, String intention, String packetId, String requestString) {
		System.out.println("JsonOrJsonpSeedHandler::fromProcess(" + uid + ", " + providerGroups + ", " + intention + ", " + packetId + ", " + requestString);
		SeedResponse response = new SeedResponse(uid, providerGroups, intention, packetId);
		System.out.println("JsonOrJsonpSeedHandler::fromProcess: response = " + response);
		ModelContainer model = null;
		try {
			requestString = StringUtils.trimToNull(requestString);
			requireNotNull(requestString, "The requests parameter may not be null.");
			SeedRequest[] request = MolecularModelJsonRenderer.parseFromJson(requestString, requestType);
			requireNotNull(request, "The requests array may not be null");
			if (request.length == 0 || request[0] == null || request.length > 1) {
				throw new MissingParameterException("The requests array must contain exactly one non-null entry");
			}
			uid = normalizeUserId(uid);
			UndoMetadata token = new UndoMetadata(uid);
			System.out.println("JsonOrJsonpSeedHandler::fromProcess: uid: " + uid);
			System.out.println("JsonOrJsonpSeedHandler::fromProcess: token: " + token);
			model = createModel(uid, providerGroups, token, VariableResolver.EMPTY, null);
			return seedFromProcess(uid, providerGroups, request[0].arguments, model, response, token);
		} catch (Exception e) {
			deleteModel(model);
			return error(response, "Could not successfully handle batch request.", e);
		} catch (Throwable t) {
			deleteModel(model);
			logger.error("A critical error occured.", t);
			return error(response, "An internal error occured at the server level.", t);
		}
	}
	
	private SeedResponse seedFromProcess(String uid, Set<String> providerGroups, SeedRequestArgument request, ModelContainer model, SeedResponse response, UndoMetadata token) throws Exception {
		System.out.println("JsonOrJsonpSeedHandler::seedFromProcess(" + uid + ", " + providerGroups + ", " + request + ", " + model + ", " + response + ", " + token);

		// check required fields
		requireNotNull(request.process, "A process id is required for seeding");
		requireNotNull(request.taxon, "A taxon id is required for seeding");
		
		// prepare seeder
		OWLGraphWrapper graph = new OWLGraphWrapper(model.getAboxOntology());
		Set<OWLClass> locationRoots = new HashSet<OWLClass>();
		if (request.locationRoots != null) {
			for(String loc : request.locationRoots) {
				OWLClass cls = graph.getOWLClassByIdentifier(loc);
				if (cls != null) {
					locationRoots.add(cls);
				}
			}
		}
		Set<String> evidenceRestriction = request.evidenceRestriction != null ? new HashSet<>(Arrays.asList(request.evidenceRestriction)) : null;
		Set<String> blackList = request.ignoreList != null ? new HashSet<>(Arrays.asList(request.ignoreList)) : null;
		Set<String> taxonRestriction = Collections.singleton(request.taxon);
		ExpressionMaterializingReasoner reasoner = null;
		try {
			reasoner = factory.createReasoner(model.getAboxOntology());
			reasoner.setIncludeImports(true);
			GolrSeedingDataProvider provider = new GolrSeedingDataProvider(golrUrl, graph, 
					reasoner, locationRoots, evidenceRestriction, taxonRestriction, blackList) {

						@Override
						protected void logRequest(URI uri) {
							logGolrRequest(uri);
						}
					
					};
			Set<OWLAnnotation> defaultAnnotations = new HashSet<OWLAnnotation>();
			System.out.println("JsonOrJsonpSeedHandler::seedFromProcess: calling addGeneratedAnnotations");
			addGeneratedAnnotations(uid, providerGroups, defaultAnnotations, model.getOWLDataFactory());
			System.out.println("JsonOrJsonpSeedHandler::seedFromProcess: calling addDateAnnotation");
			addDateAnnotation(defaultAnnotations, model.getOWLDataFactory());
			ModelSeeding<UndoMetadata> seeder = new ModelSeeding<UndoMetadata>(reasoner, provider, defaultAnnotations, curieHandler, ecoMapper);

			// seed
			seeder.seedModel(model, m3, request.process, token);

			// render result
			// create response.data
			response.messageType = SeedResponse.MESSAGE_TYPE_SUCCESS;
			response.message = SeedResponse.MESSAGE_TYPE_SUCCESS;
			response.signal = SeedResponse.SIGNAL_META;
			response.data = new SeedResponseData();

			// model id
			response.data.id = curieHandler.getCuri(model.getModelId());
			return response;
		}
		finally {
			if (reasoner != null) {
				reasoner.dispose();
			}
		}
	}
	
	protected void logGolrRequest(URI uri) {
		// empty, overwrite for custom logging
	}
	
	/*
	 * commentary is now to be a string, not an unknown multi-leveled object.
	 */
	private SeedResponse error(SeedResponse state, String msg, Throwable e) {
		state.messageType = "error";
		state.message = msg;
		if (e != null) {

			// Add in the exception name if possible.
			String ename = e.getClass().getName();
			if( ename != null ){
				state.message = state.message + " Exception: " + ename + ".";
			}
			
			// And the exception message.
			String emsg = e.getMessage();
			if( emsg != null ){
				state.message = state.message + " " + emsg;
			}
			
			// Add the stack trace as commentary.
			StringWriter stacktrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stacktrace));
			state.commentary = stacktrace.toString();
		}
		return state;
	}
}
