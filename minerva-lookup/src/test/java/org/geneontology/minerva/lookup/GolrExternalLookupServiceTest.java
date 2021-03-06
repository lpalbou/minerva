package org.geneontology.minerva.lookup;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bbop.golr.java.RetrieveGolrBioentities;
import org.bbop.golr.java.RetrieveGolrOntologyClass;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.lookup.CachingExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.GolrExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;

public class GolrExternalLookupServiceTest {
	
	private static final String golrUrl = "http://golr.berkeleybop.org/solr";
	private final CurieHandler handler = DefaultCurieHandler.getDefaultHandler();

	@Test
	public void testLookupString1() throws Exception {
		GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler, true);
		String testCurie = "SGD:S000004529";
		List<LookupEntry> lookup = s.lookup(handler.getIRI(testCurie));
		assertEquals(1, lookup.size());
		assertEquals("TEM1", lookup.get(0).label);
		
		IRI testIRI = IRI.create("http://identifiers.org/sgd/S000004529");
		assertEquals(testCurie, handler.getCuri(testIRI));
		List<LookupEntry> lookup2 = s.lookup(testIRI);
		assertEquals(1, lookup2.size());
		assertEquals("TEM1", lookup2.get(0).label);
	}
	
	@Test
	public void testLookupString2() throws Exception {
		GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
		List<LookupEntry> lookup = s.lookup(handler.getIRI("SGD:S000004328"));
		assertEquals(1, lookup.size());
		assertEquals("SGD1", lookup.get(0).label);
	}
	
	@Test
	public void testLookupString3() throws Exception {
		GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
		String testCurie = "SGD:S000005952";
		List<LookupEntry> lookup = s.lookup(handler.getIRI(testCurie));
		assertEquals(1, lookup.size());
		assertEquals("PHO85", lookup.get(0).label);
		
		IRI testIRI = IRI.create("http://identifiers.org/sgd/S000005952");
		assertEquals(testCurie, handler.getCuri(testIRI));
		List<LookupEntry> lookup2 = s.lookup(testIRI);
		assertEquals(1, lookup2.size());
		assertEquals("PHO85", lookup2.get(0).label);
		
	}
	
	@Test
	public void testLookupStringCls() throws Exception {
		GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
		List<LookupEntry> lookup = s.lookup(handler.getIRI("PO:0001040"));
		assertEquals(1, lookup.size());
		assertEquals("dry seed stage", lookup.get(0).label);
	}
	
	@Test
	public void testLookupStringCls2() throws Exception {
		Logger.getLogger(GolrExternalLookupService.class).setLevel(Level.DEBUG);
		GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
		List<LookupEntry> lookup = s.lookup(handler.getIRI("UBERON:0010403"));
		assertEquals(1, lookup.size());
		assertEquals("brain marginal zone", lookup.get(0).label);
	}
	
	@Test
	public void testCachedGolrLookup() throws Exception {
		final List<URI> requests = new ArrayList<URI>();
		GolrExternalLookupService golr = new GolrExternalLookupService(golrUrl, 
		new RetrieveGolrBioentities(golrUrl, 2){

			@Override
			protected void logRequest(URI uri) {
				requests.add(uri);
			}
			
		}, new RetrieveGolrOntologyClass(golrUrl, 2){
			@Override
			protected void logRequest(URI uri) {
				requests.add(uri);
			}
		}, handler);
		ExternalLookupService s = new CachingExternalLookupService(golr, 1000, 24l, TimeUnit.HOURS);
		
		List<LookupEntry> lookup1 = s.lookup(handler.getIRI("SGD:S000004529"));
		assertEquals(1, lookup1.size());
		assertEquals("TEM1", lookup1.get(0).label);
		int count = requests.size();
		
		List<LookupEntry> lookup2 = s.lookup(handler.getIRI("SGD:S000004529"));
		assertEquals(1, lookup2.size());
		assertEquals("TEM1", lookup2.get(0).label);
		
		// there should be no new request to Golr, that's what the cache is for!
		assertEquals(count, requests.size());
	}

}
