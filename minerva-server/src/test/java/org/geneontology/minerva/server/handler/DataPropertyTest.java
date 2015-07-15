package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonModel;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.JsonRelationInfo;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.server.handler.JsonOrJsonpBatchHandler;
import org.geneontology.minerva.server.handler.M3BatchHandler.Entity;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Argument;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Request;
import org.geneontology.minerva.server.handler.M3BatchHandler.Operation;
import org.geneontology.minerva.util.IdStringManager.AnnotationShorthand;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import owltools.graph.OWLGraphWrapper;

public class DataPropertyTest {

	@Test
	public void testDataPropertyMetadata() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = m.createOntology(IRI.generateDocumentIRI());
		{
			// create a test ontology with one data property
			OWLDataFactory f = m.getOWLDataFactory();
			IRI propIRI = IRI.generateDocumentIRI();
			OWLDataProperty prop = f.getOWLDataProperty(propIRI);
			m.addAxiom(ontology, f.getOWLDeclarationAxiom(prop));
			m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(propIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-data-property"))));
		}
		OWLGraphWrapper graph = new OWLGraphWrapper(ontology);
		MolecularModelManager<?> mmm = new MolecularModelManager<Object>(graph, new ElkReasonerFactory(),
				"http://model.geneontology.org/", "gomodel:");
		Pair<List<JsonRelationInfo>,List<JsonRelationInfo>> pair = MolecularModelJsonRenderer.renderProperties(mmm, null);
		List<JsonRelationInfo> dataProperties = pair.getRight();
		assertEquals(1, dataProperties.size());
	}
	
	@Test
	public void testDataProperyRenderer() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = m.createOntology(IRI.generateDocumentIRI());
		final IRI clsIRI = IRI.generateDocumentIRI();
		final IRI propIRI = IRI.generateDocumentIRI();
		
		// create a test ontology with one data property and one class
		OWLDataFactory f = m.getOWLDataFactory();
		OWLDataProperty prop = f.getOWLDataProperty(propIRI);
		m.addAxiom(ontology, f.getOWLDeclarationAxiom(prop));
		m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(propIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-data-property"))));

		OWLClass cls = f.getOWLClass(clsIRI);
		m.addAxiom(ontology, f.getOWLDeclarationAxiom(cls));
		m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(clsIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-cls"))));
		
		// graph and m3
		OWLGraphWrapper graph = new OWLGraphWrapper(ontology);
		final Object metadata = new Object();
		MolecularModelManager<Object> m3 = new MolecularModelManager<Object>(graph, new ElkReasonerFactory(),
				"http://model.geneontology.org/", "gomodel:");
		
		final String modelId = m3.generateBlankModel(metadata);
		final ModelContainer model = m3.getModel(modelId);
		final OWLNamedIndividual individual = m3.createIndividual(model, cls, metadata);
		m3.addDataProperty(model, individual, prop, f.getOWLLiteral(10), false, metadata);
		
		MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(model, null);
		final JsonModel jsonModel = r.renderModel();
		assertEquals(1, jsonModel.individuals.length);
		assertEquals(1, jsonModel.individuals[0].annotations.length);
		{
			JsonAnnotation ann = jsonModel.individuals[0].annotations[0];
			assertEquals(propIRI.toString(), ann.key);
			assertEquals("10", ann.value);
			assertEquals("xsd:integer", ann.valueType);
		}
	}
	
	@Test
	public void testDataPropertyBatch() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = m.createOntology(IRI.generateDocumentIRI());
		final IRI clsIRI = IRI.create("http://purl.obolibrary.org/obo/CLS_0001");
		final IRI propIRI = IRI.create("http://purl.obolibrary.org/obo/PROP_0001");
		
		// create a test ontology with one data property and one class
		OWLDataFactory f = m.getOWLDataFactory();
		OWLDataProperty prop = f.getOWLDataProperty(propIRI);
		m.addAxiom(ontology, f.getOWLDeclarationAxiom(prop));
		m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(propIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-data-property"))));

		OWLClass cls = f.getOWLClass(clsIRI);
		m.addAxiom(ontology, f.getOWLDeclarationAxiom(cls));
		m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(clsIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-cls"))));
		
		// graph and m3
		OWLGraphWrapper graph = new OWLGraphWrapper(ontology);
		UndoAwareMolecularModelManager m3 = new UndoAwareMolecularModelManager(graph, new ElkReasonerFactory(),
				"http://model.geneontology.org/", "gomodel:");
		
		// handler
		boolean useReasoner = false;
		boolean useModelReasoner = false;
		JsonOrJsonpBatchHandler handler = new JsonOrJsonpBatchHandler(m3, useReasoner, useModelReasoner, null, null);
		
		// empty model
		final String modelId = m3.generateBlankModel(new UndoMetadata("foo-user"));
		
		// create individual with annotations, including one data property
		M3Request r1 = BatchTestTools.addIndividual(modelId, "CLS:0001");
		r1.arguments.values = new JsonAnnotation[2];
		r1.arguments.values[0] = new JsonAnnotation();
		r1.arguments.values[0].key = AnnotationShorthand.comment.name();
		r1.arguments.values[0].value = "foo-comment";
		r1.arguments.values[1] = new JsonAnnotation();
		r1.arguments.values[1].key = propIRI.toString();
		r1.arguments.values[1].value = "10";
		r1.arguments.values[1].valueType = "xsd:integer";
		
		M3BatchResponse response1 = exec(handler, Collections.singletonList(r1));
		
		final String individualsId;
		// check for data property as annotation
		{
			assertEquals(1, response1.data.individuals.length);
			JsonOwlIndividual i = response1.data.individuals[0];
			assertEquals(4, i.annotations.length);
			individualsId = i.id;
			JsonAnnotation dataPropAnnotation = null;
			for(JsonAnnotation ann : i.annotations) {
				if (propIRI.toString().equals(ann.key)) {
					dataPropAnnotation = ann;
				}
			}
			assertNotNull(dataPropAnnotation);
		}
		assertNotNull(individualsId);
		
		// check underlying owl model for usage of OWLDataProperty
		final ModelContainer model = m3.getModel(modelId);
		{
			Set<OWLDataPropertyAssertionAxiom> axioms = model.getAboxOntology().getAxioms(AxiomType.DATA_PROPERTY_ASSERTION);
			assertEquals(1, axioms.size());
			OWLDataPropertyAssertionAxiom ax = axioms.iterator().next();
			OWLLiteral literal = ax.getObject();
			assertEquals(prop, ax.getProperty());
			assertEquals(f.getOWLLiteral(10), literal);
		}
		
		// delete data property
		M3Request r2 = new M3Request();
		r2.entity = Entity.individual;
		r2.operation = Operation.removeAnnotation;
		r2.arguments = new M3Argument();
		r2.arguments.individual = individualsId;
		r2.arguments.modelId = modelId;
		r2.arguments.values = new JsonAnnotation[1];
		r2.arguments.values[0] = new JsonAnnotation();
		r2.arguments.values[0].key = propIRI.toString();
		r2.arguments.values[0].value = "10";
		r2.arguments.values[0].valueType = "xsd:integer";
		
		M3BatchResponse response2 = exec(handler, Collections.singletonList(r2));
		// check for deleted property as annotation
		{
			assertEquals(1, response2.data.individuals.length);
			JsonOwlIndividual i = response2.data.individuals[0];
			assertEquals(3, i.annotations.length);
		}
	}
	
	private M3BatchResponse exec(JsonOrJsonpBatchHandler handler, List<M3Request> requests) {
		String uid = "foo-user";
		String intention = "generated";
		String packetId = "0";
		M3BatchResponse response = handler.m3Batch(uid, intention, packetId, requests.toArray(new M3Request[requests.size()]), true);
		assertEquals(uid, response.uid);
		assertEquals(intention, response.intention);
		assertEquals(packetId, response.packetId);
		assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
		return response;
	}
}
