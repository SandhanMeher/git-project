package com.infinite.dao;

import java.util.ArrayList; // Added for returning empty lists instead of null
import java.util.List;

import org.apache.log4j.Logger; // Import Log4j Logger
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.Query; // Use this in Hibernate 3.6 (not javax.persistence.Query)

import com.infinite.model.Recipient;
import com.infinite.util.SessionHelper;

/**
 * `RecipientDaoImpl` provides the concrete implementation for data access
 * operations related to `Recipient` entities using Hibernate. It includes
 * methods for searching, retrieving, saving, updating, and deleting recipient
 * information.
 */
public class RecipientDaoImpl implements RecipientDao { // Implemented the interface

	private static final Logger logger = Logger.getLogger(RecipientDaoImpl.class);

	/**
	 * Searches for a `Recipient` by their unique healthcare ID.
	 *
	 * @param hId The unique healthcare ID of the recipient.
	 * @return The `Recipient` object if found, otherwise null.
	 */
	public Recipient searchRecipientById(String hId) {
		Session session = null;
		Recipient recipient = null;
		logger.info("Searching for recipient by ID: " + hId);
		try {
			session = SessionHelper.getSessionFactory().openSession();
			recipient = (Recipient) session.get(Recipient.class, hId);
			if (recipient != null) {
				logger.debug("Recipient with ID " + hId + " found.");
			} else {
				logger.warn("Recipient with ID " + hId + " not found.");
			}
		} catch (Exception e) {
			logger.error("Error searching for recipient by ID " + hId + ": " + e.getMessage(), e);
		} finally {
			if (session != null && session.isOpen()) {
				session.close();
				logger.debug("Hibernate session closed after searchRecipientById operation.");
			}
		}
		return recipient;
	}

	/**
	 * Retrieves a list of all `Recipient` entities from the database.
	 *
	 * @return A List of `Recipient` objects. Returns an empty list if no recipients
	 * are found or in case of an error.
	 */
	@SuppressWarnings("unchecked")
	public List<Recipient> getAllRecipients() {
		Session session = null;
		List<Recipient> recipients = new ArrayList<>(); // Initialize to empty list
		logger.info("Fetching all recipients.");
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Query query = session.createQuery("from Recipient");
			recipients = query.list();
			logger.debug("Found " + recipients.size() + " recipients.");
		} catch (Exception e) {
			logger.error("Error fetching all recipients: " + e.getMessage(), e);
		} finally {
			if (session != null && session.isOpen()) {
				session.close();
				logger.debug("Hibernate session closed after getAllRecipients operation.");
			}
		}
		return recipients;
	}

	/**
	 * Saves a new `Recipient` entity to the database.
	 *
	 * @param recipient The `Recipient` object to be saved.
	 * @return A String message indicating the success or failure of the save operation.
	 */
	public String saveRecipient(Recipient recipient) {
		Session session = null;
		Transaction tx = null;
		String result;
		logger.info("Attempting to save recipient with ID: " + (recipient != null ? recipient.getH_id() : "null"));
		try {
			session = SessionHelper.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.save(recipient);
			tx.commit();
			result = "Recipient saved successfully.";
			logger.info("Recipient " + recipient.getH_id() + " saved successfully.");
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
				logger.error("Transaction rolled back due to error saving recipient.", e);
			}
			result = "Error saving recipient: " + e.getMessage();
			logger.error("Error saving recipient " + (recipient != null ? recipient.getH_id() : "null") + ": " + e.getMessage(), e);
		} finally {
			if (session != null && session.isOpen()) {
				session.close();
				logger.debug("Hibernate session closed after saveRecipient operation.");
			}
		}
		return result;
	}

	/**
	 * Updates an existing `Recipient` entity in the database.
	 *
	 * @param recipient The `Recipient` object with updated details.
	 * @return A String message indicating the success or failure of the update operation.
	 */
	public String updateRecipient(Recipient recipient) {
		Session session = null;
		Transaction tx = null;
		String result;
		logger.info("Attempting to update recipient with ID: " + (recipient != null ? recipient.getH_id() : "null"));
		try {
			session = SessionHelper.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.update(recipient);
			tx.commit();
			result = "Recipient updated successfully.";
			logger.info("Recipient " + recipient.getH_id() + " updated successfully.");
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
				logger.error("Transaction rolled back due to error updating recipient.", e);
			}
			result = "Error updating recipient: " + e.getMessage();
			logger.error("Error updating recipient " + (recipient != null ? recipient.getH_id() : "null") + ": " + e.getMessage(), e);
		} finally {
			if (session != null && session.isOpen()) {
				session.close();
				logger.debug("Hibernate session closed after updateRecipient operation.");
			}
		}
		return result;
	}

	/**
	 * Deletes a `Recipient` entity from the database based on their unique healthcare ID.
	 *
	 * @param hId The unique healthcare ID of the recipient to be deleted.
	 * @return A String message indicating the success or failure of the delete operation.
	 */
	public String deleteRecipient(String hId) {
		Session session = null;
		Transaction tx = null;
		String result;
		logger.info("Attempting to delete recipient with ID: " + hId);
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Recipient recipient = (Recipient) session.get(Recipient.class, hId);
			if (recipient != null) {
				tx = session.beginTransaction();
				session.delete(recipient);
				tx.commit();
				result = "Recipient deleted successfully.";
				logger.info("Recipient " + hId + " deleted successfully.");
			} else {
				result = "Recipient not found.";
				logger.warn("Recipient with ID " + hId + " not found for deletion.");
			}
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
				logger.error("Transaction rolled back due to error deleting recipient.", e);
			}
			result = "Error deleting recipient: " + e.getMessage();
			logger.error("Error deleting recipient " + hId + ": " + e.getMessage(), e);
		} finally {
			if (session != null && session.isOpen()) {
				session.close();
				logger.debug("Hibernate session closed after deleteRecipient operation.");
			}
		}
		return result;
	}
}