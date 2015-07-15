package org.geneontology.minerva.format;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.format.LegoModelVersionConverter;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

import com.google.common.collect.Sets;

public class LegoModelVersionConverterTest {

	@Test
	public void testConvertLegoModelToAllIndividuals() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		OWLGraphWrapper graph = new OWLGraphWrapper(pw.parse("http://purl.obolibrary.org/obo/go.owl"));
		MolecularModelManager<?> m3 = new MolecularModelManager<Object>(graph, new ElkReasonerFactory(),
				"http://model.geneontology.org/", "gomodel:");
		m3.setPathToOWLFiles("src/test/resources/lego-conversion");
		Map<String, String> modelIds = m3.getAvailableModelIds();
		assertEquals(1, modelIds.size());
		
		final String modelId = modelIds.keySet().iterator().next();
		final ModelContainer model = m3.getModel(modelId);
		assertNotNull(model);
		OWLOntology aboxOntology = model.getAboxOntology();
		Set<OWLNamedIndividual> allIndividualsOld = aboxOntology.getIndividualsInSignature();
		
		LegoModelVersionConverter converter = new LegoModelVersionConverter();
		converter.convertLegoModelToAllIndividuals(model.getAboxOntology(), modelId);
		
		
		Set<OWLNamedIndividual> allIndividualsNew = aboxOntology.getIndividualsInSignature();
		assertTrue(allIndividualsNew.size() >= allIndividualsOld.size());
		assertTrue(allIndividualsNew.containsAll(allIndividualsOld));
		Set<OWLNamedIndividual> newIndividuals = Sets.difference(allIndividualsNew, allIndividualsOld);
		Set<OWLNamedIndividual> ecoIndividuals = new HashSet<OWLNamedIndividual>();
		for (OWLNamedIndividual newIndividual : newIndividuals) {
			IRI iri = newIndividual.getIRI();
			if (iri.toString().contains("-ECO-")) {
				ecoIndividuals.add(newIndividual);
			}
		}
		//assertEquals(3, ecoIndividuals.size());
		
		System.out.println("---------");
		System.out.println(renderModel(model));
		System.out.println("---------");
		
		System.out.println("----------");
		System.out.println(rendertoJson(model));
		System.out.println("----------");
		
	}

	static String rendertoJson(ModelContainer model) {
		return MolecularModelJsonRenderer.renderToJson(model.getAboxOntology(), null, true);
	}
	
	static String renderModel(ModelContainer model) throws Exception {
		OWLOntology aboxOntology = model.getAboxOntology();
		OWLOntologyManager m = aboxOntology.getOWLOntologyManager();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			m.saveOntology(aboxOntology, outputStream);
			return outputStream.toString();
		}
		finally {
			IOUtils.closeQuietly(outputStream);
		}
	}
}
