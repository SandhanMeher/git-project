package com.infinite.dao;

import java.util.ArrayList; // Added for returning empty lists instead of null
import java.util.List;

import org.apache.log4j.Logger; // Import Log4j Logger
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import com.infinite.model.Providers;
import com.infinite.util.SessionHelper;

/**
 * `ProviderDaoImpl` provides the concrete implementation for data access
 * operations related to `Providers` entities using Hibernate. It fetches
 * provider information based on various criteria.
 */
public class ProviderDaoImpl implements ProviderDao {

    private static final Logger logger = Logger.getLogger(ProviderDaoImpl.class);

    private SessionFactory sessionFactory;

    /**
     * Constructs a new `ProviderDaoImpl` and initializes the Hibernate
     * `SessionFactory` using `SessionHelper`.
     */
    public ProviderDaoImpl() {
        this.sessionFactory = SessionHelper.getSessionFactory();
        logger.info("ProviderDaoImpl initialized.");
    }

    /**
     * Retrieves a list of all healthcare providers who have an 'APPROVED' status.
     *
     * @return A List of `Providers` objects representing all approved providers.
     * Returns an empty list if no approved providers are found or in case of an error.
     */
    @Override
    public List<Providers> getAllApprovedProvider() {
        Session session = null;
        List<Providers> providers = new ArrayList<>(); // Initialize to empty list
        logger.info("Fetching all approved providers.");
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery("from Providers where status = 'APPROVED'");
            providers = query.list();
            logger.debug("Found " + providers.size() + " approved providers.");
        } catch (Exception e) {
            logger.error("Error fetching all approved providers: " + e.getMessage(), e);
        } finally {
            if (session != null) {
                session.close();
                logger.debug("Hibernate session closed after getAllApprovedProvider operation.");
            }
        }
        return providers;
    }

    /**
     * Searches for and retrieves a single provider by their unique provider ID.
     *
     * @param providerId The unique identifier of the provider to search for.
     * @return A `Providers` object if a provider with the specified ID is found,
     * otherwise `null`.
     */
    @Override
    public Providers searchProviderById(String providerId) {
        Session session = null;
        Providers provider = null;
        logger.info("Searching for provider by ID: " + providerId);
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery("from Providers where provider_id = :pid");
            query.setParameter("pid", providerId);
            provider = (Providers) query.uniqueResult();
            if (provider != null) {
                logger.debug("Provider with ID " + providerId + " found.");
            } else {
                logger.warn("Provider with ID " + providerId + " not found.");
            }
        } catch (Exception e) {
            logger.error("Error searching for provider by ID " + providerId + ": " + e.getMessage(), e);
        } finally {
            if (session != null) {
                session.close();
                logger.debug("Hibernate session closed after searchProviderById operation.");
            }
        }
        return provider;
    }
}