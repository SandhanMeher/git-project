package com.infinite.dao;

import java.util.List;

import com.infinite.model.Providers;

/**
 * The `ProviderDao` interface defines the contract for data access operations
 * related to `Providers` entities. It specifies methods for retrieving provider
 * information from the persistence layer.
 */
public interface ProviderDao {

	/**
	 * Retrieves a list of all providers who have an 'APPROVED' status.
	 *
	 * @return A {@code List} of {@code Providers} objects representing all approved providers.
	 */
	public List<Providers> getAllApprovedProvider();

	/**
	 * Searches for and retrieves a single provider by their unique provider ID.
	 *
	 * @param providerId The unique identifier of the provider to search for.
	 * @return A {@code Providers} object if a provider with the specified ID is found,
	 * otherwise {@code null}.
	 */
	public Providers searchProviderById(String providerId);
}