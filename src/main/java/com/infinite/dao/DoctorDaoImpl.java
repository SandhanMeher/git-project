package com.infinite.dao;

import java.util.ArrayList; // Added for returning empty lists instead of null
import java.util.List;

import org.apache.log4j.Logger; // Import Log4j Logger
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import com.infinite.model.Doctors;
import com.infinite.util.SessionHelper;

/**
 * `DoctorDaoImpl` provides the concrete implementation for data access
 * operations related to `Doctors` entities using Hibernate. It fetches
 * doctor information based on various criteria.
 */
public class DoctorDaoImpl implements DoctorDao {

	private static final Logger logger = Logger.getLogger(DoctorDaoImpl.class);

	private SessionFactory sessionFactory;

	/**
	 * Constructs a new `DoctorDaoImpl` and initializes the Hibernate
	 * `SessionFactory` using `SessionHelper`.
	 */
	public DoctorDaoImpl() {
		this.sessionFactory = SessionHelper.getSessionFactory();
		logger.info("DoctorDaoImpl initialized.");
	}

	/**
	 * Retrieves a list of all doctors who have an 'APPROVED' login status.
	 *
	 * @return A List of `Doctors` objects representing all approved doctors.
	 * Returns an empty list if no approved doctors are found or in case of an error.
	 */
	@Override
	public List<Doctors> getAllApprovedDoctor() {
		Session session = null;
		List<Doctors> doctors = new ArrayList<>(); // Initialize to empty list
		logger.info("Fetching all approved doctors.");

		try {
			session = sessionFactory.openSession();
			Query query = session.createQuery("from Doctors where login_status = 'APPROVED'");
			doctors = query.list();
			logger.debug("Found " + doctors.size() + " approved doctors.");
		} catch (Exception e) {
			logger.error("Error fetching all approved doctors: " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAllApprovedDoctor operation.");
			}
		}
		return doctors;
	}

	/**
	 * Retrieves a list of all doctors associated with a specific healthcare provider
	 * who also have an 'APPROVED' login status.
	 *
	 * @param providerId The unique identifier of the healthcare provider.
	 * @return A List of `Doctors` objects representing approved doctors
	 * under the given provider. Returns an empty list if no approved doctors
	 * for the provider are found or in case of an error.
	 */
	@Override
	public List<Doctors> getApprovedDoctorsByProviderId(String providerId) {
		Session session = null;
		List<Doctors> doctors = new ArrayList<>(); // Initialize to empty list
		logger.info("Fetching approved doctors for provider ID: " + providerId);

		try {
			session = sessionFactory.openSession();
			Query query = session
					.createQuery("from Doctors where login_status = 'APPROVED' and provider.provider_id = :pid");
			query.setParameter("pid", providerId);
			doctors = query.list();
			logger.debug("Found " + doctors.size() + " approved doctors for provider " + providerId + ".");
		} catch (Exception e) {
			logger.error("Error fetching approved doctors for provider " + providerId + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getApprovedDoctorsByProviderId operation.");
			}
		}
		return doctors;
	}

	/**
	 * Searches for and retrieves a single doctor by their unique doctor ID.
	 *
	 * @param doctorId The unique identifier of the doctor to search for.
	 * @return A `Doctors` object if a doctor with the specified ID is found,
	 * otherwise `null`.
	 */
	@Override
	public Doctors searchADoctorById(String doctorId) {
		Session session = null;
		Doctors doctor = null;
		logger.info("Searching for doctor by ID: " + doctorId);

		try {
			session = sessionFactory.openSession();
			Query query = session.createQuery("from Doctors where doctor_id = :docId");
			query.setParameter("docId", doctorId);
			doctor = (Doctors) query.uniqueResult();
			if (doctor != null) {
				logger.debug("Doctor with ID " + doctorId + " found.");
			} else {
				logger.warn("Doctor with ID " + doctorId + " not found.");
			}
		} catch (Exception e) {
			logger.error("Error searching for doctor by ID " + doctorId + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after searchADoctorById operation.");
			}
		}
		return doctor;
	}
}