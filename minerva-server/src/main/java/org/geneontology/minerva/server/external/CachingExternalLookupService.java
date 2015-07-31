package org.geneontology.minerva.server.external;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.semanticweb.owlapi.model.IRI;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;

public class CachingExternalLookupService implements ExternalLookupService {
	
	private final LoadingCache<IRI, List<LookupEntry>> cache;
	private final ExternalLookupService service;
	
	public CachingExternalLookupService(ExternalLookupService service, int size) {
		this.service = service;
		cache = CacheBuilder.newBuilder()
				.maximumSize(size)
				.build(new CacheLoader<IRI, List<LookupEntry>>() {

					@Override
					public List<LookupEntry> load(IRI key) throws Exception {
						List<LookupEntry> lookup = CachingExternalLookupService.this.service.lookup(key);
						if (lookup == null) {
							throw new Exception("No legal value for key.");
						}
						return lookup;
					}
				});
	}
	
	public CachingExternalLookupService(Iterable<ExternalLookupService> services, int size) {
		this(new CombinedExternalLookupService(services), size);
	}

	public CachingExternalLookupService(int size, ExternalLookupService...services) {
		this(Arrays.asList(services), size);
	}
	
	@Override
	public List<LookupEntry> lookup(IRI id) {
		try {
			return cache.get(id);
		} catch (ExecutionException e) {
			return null;
		} catch (UncheckedExecutionException e) {
			return null;
		} catch (ExecutionError e) {
			return null;
		}
	}

	@Override
	public LookupEntry lookup(IRI id, String taxon) {
		LookupEntry entry = null;
		List<LookupEntry> list = cache.getUnchecked(id);
		for (LookupEntry current : list) {
			if (taxon.equals(current.taxon)) {
				entry = current;
				break;
			}
		}
		return entry;
	}

	@Override
	public String toString() {
		return "Caching("+service.toString()+")";
	}
	
	

}
