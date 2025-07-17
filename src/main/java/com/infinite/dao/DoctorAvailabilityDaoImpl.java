package com.infinite.dao;

import java.sql.Date;
import java.util.ArrayList; // Added for returning empty lists instead of null
import java.util.List;

import org.apache.log4j.Logger; // Import Log4j Logger
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction; // Explicitly imported for clarity

import com.infinite.model.DoctorAvailability;
import com.infinite.util.SessionHelper;

/**
 * `DoctorAvailabilityDaoImpl` provides the concrete implementation for managing
 * doctor availability data operations in the database. It includes methods
 * for retrieving, adding, updating, and deleting doctor availability slots.
 */
public class DoctorAvailabilityDaoImpl implements DoctorAvailabilityDao {

	private static final Logger logger = Logger.getLogger(DoctorAvailabilityDaoImpl.class);

	private SessionFactory sessionFactory;

	/**
	 * Constructs a new `DoctorAvailabilityDaoImpl` and initializes the Hibernate
	 * `SessionFactory` using `SessionHelper`.
	 */
	public DoctorAvailabilityDaoImpl() {
		this.sessionFactory = SessionHelper.getSessionFactory();
		logger.info("DoctorAvailabilityDaoImpl initialized.");
	}

	/**
	 * Retrieves a list of available doctor slots for a specific doctor on a given date.
	 * The results are ordered by start time in ascending order.
	 *
	 * @param doctorId The ID of the doctor.
	 * @param date     The specific date to retrieve availability for.
	 * @return A list of `DoctorAvailability` objects. Returns an empty list if no slots are found or in case of an error.
	 */
	@Override
	public List<DoctorAvailability> getAvailableSlotsByDoctorAndDate(String doctorId, Date date) {
		Session session = null;
		List<DoctorAvailability> slots = new ArrayList<>(); // Initialize to empty list
		logger.info("Fetching available slots for doctor ID: " + doctorId + " on date: " + date);
		try {
			session = sessionFactory.openSession();
			Query query = session.createQuery(
					"from DoctorAvailability where doctor.doctor_id = :doctorId and available_date = :date order by start_time asc");
			query.setParameter("doctorId", doctorId);
			query.setParameter("date", date);
			slots = query.list();
			logger.debug("Found " + slots.size() + " available slots for doctor " + doctorId + " on " + date);
		} catch (Exception e) {
			logger.error("Error fetching available slots for doctor " + doctorId + " on " + date + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAvailableSlotsByDoctorAndDate operation.");
			}
		}
		return slots;
	}

	/**
	 * Retrieves a `DoctorAvailability` object by its unique availability ID.
	 *
	 * @param availabilityId The unique ID of the doctor availability.
	 * @return The `DoctorAvailability` object if found, otherwise null.
	 */
	@Override
	public DoctorAvailability getAvailabilityById(String availabilityId) {
		Session session = null;
		DoctorAvailability availability = null;
		logger.info("Fetching availability by ID: " + availabilityId);
		try {
			session = sessionFactory.openSession();
			availability = (DoctorAvailability) session.get(DoctorAvailability.class, availabilityId);
			if (availability != null) {
				logger.debug("Availability " + availabilityId + " found.");
			} else {
				logger.warn("Availability with ID " + availabilityId + " not found.");
			}
		} catch (Exception e) {
			logger.error("Error fetching availability by ID " + availabilityId + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAvailabilityById operation.");
			}
		}
		return availability;
	}

	/**
	 * Retrieves a list of upcoming availabilities for a specific doctor, starting from a given date.
	 * The results are ordered by available date and then by start time in ascending order.
	 *
	 * @param doctorId The ID of the doctor.
	 * @param fromDate The starting date from which to retrieve upcoming availabilities.
	 * @return A list of `DoctorAvailability` objects. Returns an empty list if no upcoming slots are found or in case of an error.
	 */
	@Override
	public List<DoctorAvailability> getUpcomingAvailabilitiesForDoctor(String doctorId, Date fromDate) {
		Session session = null;
		List<DoctorAvailability> slots = new ArrayList<>(); // Initialize to empty list
		logger.info("Fetching upcoming availabilities for doctor ID: " + doctorId + " from date: " + fromDate);
		try {
			session = sessionFactory.openSession();
			Query query = session.createQuery(
					"from DoctorAvailability where doctor.doctor_id = :doctorId and available_date >= :fromDate order by available_date, start_time asc");
			query.setParameter("doctorId", doctorId);
			query.setParameter("fromDate", fromDate);
			slots = query.list();
			logger.debug("Found " + slots.size() + " upcoming availabilities for doctor " + doctorId + " from " + fromDate);
		} catch (Exception e) {
			logger.error("Error fetching upcoming availabilities for doctor " + doctorId + " from " + fromDate + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getUpcomingAvailabilitiesForDoctor operation.");
			}
		}
		return slots;
	}

	/**
	 * Retrieves a list of all doctor availabilities for a specific doctor on a given date.
	 * This method is similar to `getAvailableSlotsByDoctorAndDate` but may be used for
	 * a broader purpose, e.g., for doctor's own view (including potentially full slots).
	 * The results are ordered by start time.
	 *
	 * @param doctorId The ID of the doctor.
	 * @param date     The specific date to retrieve availabilities for.
	 * @return A list of `DoctorAvailability` objects. Returns an empty list if no slots are found or in case of an error.
	 */
	@Override
	public List<DoctorAvailability> getAvailabilityByDoctorAndDate(String doctorId, Date date) {
		Session session = null;
		List<DoctorAvailability> slots = new ArrayList<>(); // Initialize to empty list
		logger.info("Fetching availabilities by doctor ID: " + doctorId + " and date: " + date);
		try {
			session = sessionFactory.openSession();
			Query query = session.createQuery(
					"from DoctorAvailability where doctor.doctor_id = :doctorId and available_date = :date order by start_time");
			query.setParameter("doctorId", doctorId);
			query.setParameter("date", date);
			slots = query.list();
			logger.debug("Found " + slots.size() + " availabilities for doctor " + doctorId + " on " + date);
		} catch (Exception e) {
			logger.error("Error fetching availabilities by doctor " + doctorId + " and date " + date + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAvailabilityByDoctorAndDate operation.");
			}
		}
		return slots;
	}

	/**
	 * Retrieves all doctor availabilities associated with a specific healthcare provider.
	 * The results are ordered by available date in descending order, then by start time in descending order.
	 *
	 * @param providerId The ID of the healthcare provider.
	 * @return A list of `DoctorAvailability` objects. Returns an empty list if no availabilities are found or in case of an error.
	 */
	@Override
	public List<DoctorAvailability> getAllAvailabilitiesByProvider(String providerId) {
		Session session = null;
		List<DoctorAvailability> list = new ArrayList<>(); // Initialize to empty list
		logger.info("Fetching all availabilities by provider ID: " + providerId);
		try {
			session = sessionFactory.openSession();
			Query query = session.createQuery(
					"from DoctorAvailability where doctor.provider.provider_id = :providerId order by available_date desc, start_time desc");
			query.setParameter("providerId", providerId);
			list = query.list();
			logger.debug("Found " + list.size() + " availabilities for provider " + providerId);
		} catch (Exception e) {
			logger.error("Error fetching all availabilities by provider " + providerId + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAllAvailabilitiesByProvider operation.");
			}
		}
		return list;
	}

	/**
	 * Adds a new doctor availability slot to the database.
	 *
	 * @param availability The `DoctorAvailability` object to be added.
	 */
	@Override
	public void addDoctorAvailability(DoctorAvailability availability) {
		Session session = null;
		Transaction tx = null;
		logger.info("Attempting to add new doctor availability.");
		try {
			session = sessionFactory.openSession();
			tx = session.beginTransaction();
			session.save(availability);
			tx.commit();
			logger.info("Doctor availability for doctor " + availability.getDoctor().getDoctor_id() + " on " + availability.getAvailable_date() + " from " + availability.getStart_time() + " successfully added.");
		} catch (Exception e) {
			logger.error("Error adding doctor availability: " + e.getMessage(), e);
			if (tx != null) {
				tx.rollback();
				logger.warn("Transaction rolled back for addDoctorAvailability.");
			}
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after addDoctorAvailability operation.");
			}
		}
	}

	/**
	 * Updates an existing doctor availability slot in the database.
	 *
	 * @param availability The `DoctorAvailability` object with updated details.
	 */
	@Override
	public void updateDoctorAvailability(DoctorAvailability availability) {
		Session session = null;
		Transaction tx = null;
		logger.info("Attempting to update doctor availability with ID: " + availability.getAvailability_id());
		try {
			session = sessionFactory.openSession();
			tx = session.beginTransaction();
			session.update(availability);
			tx.commit();
			logger.info("Doctor availability " + availability.getAvailability_id() + " successfully updated.");
		} catch (Exception e) {
			logger.error("Error updating doctor availability " + availability.getAvailability_id() + ": " + e.getMessage(), e);
			if (tx != null) {
				tx.rollback();
				logger.warn("Transaction rolled back for updateDoctorAvailability.");
			}
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after updateDoctorAvailability operation.");
			}
		}
	}

	/**
	 * Deletes a doctor availability slot only if there are no existing appointments
	 * associated with it. This prevents deletion of availability with active bookings.
	 *
	 * @param availabilityId The ID of the availability slot to potentially delete.
	 * @return true if the availability was successfully deleted (because it had no appointments), false otherwise.
	 */
	@Override
	public boolean deleteAvailabilityIfNoAppointments(String availabilityId) {
		Session session = null;
		Transaction tx = null;
		boolean deleted = false;
		logger.info("Attempting to delete availability " + availabilityId + " if no appointments exist.");
		try {
			session = sessionFactory.openSession();
			tx = session.beginTransaction();

			// Check if there are any appointments linked to this availability
			Long count = (Long) session
					.createQuery(
							"select count(*) from Appointment where availability.availability_id = :availabilityId")
					.setParameter("availabilityId", availabilityId).uniqueResult();

			if (count == 0) {
				// No appointments, proceed with deletion
				DoctorAvailability availability = (DoctorAvailability) session.get(DoctorAvailability.class,
						availabilityId);
				if (availability != null) {
					session.delete(availability);
					deleted = true;
					logger.info("Availability " + availabilityId + " successfully deleted as no appointments were linked.");
				} else {
					logger.warn("Availability " + availabilityId + " not found for deletion.");
				}
			} else {
				logger.warn("Availability " + availabilityId + " cannot be deleted as it has " + count + " linked appointments.");
			}

			tx.commit();
		} catch (Exception e) {
			logger.error("Error deleting availability " + availabilityId + ": " + e.getMessage(), e);
			if (tx != null) {
				tx.rollback();
				logger.warn("Transaction rolled back for deleteAvailabilityIfNoAppointments.");
			}
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after deleteAvailabilityIfNoAppointments operation.");
			}
		}
		return deleted;
	}

	/**
	 * Retrieves all doctor availabilities scheduled for the current date.
	 * The results are ordered by start time in ascending order.
	 *
	 * @return A list of `DoctorAvailability` objects for today's date.
	 * Returns an empty list if no availabilities are found or in case of an error.
	 */
	@Override
	public List<DoctorAvailability> getTodayAvailabilities() {
		Session session = null;
		List<DoctorAvailability> list = new ArrayList<>(); // Initialize to empty list
		logger.info("Fetching today's availabilities.");
		try {
			session = sessionFactory.openSession();
			// Using HQL's current_date() function to filter by today's date
			Query query = session
					.createQuery("from DoctorAvailability where available_date = current_date() order by start_time");
			list = query.list();
			logger.debug("Found " + list.size() + " availabilities for today.");
		} catch (Exception e) {
			logger.error("Error fetching today's availabilities: " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getTodayAvailabilities operation.");
			}
		}
		return list;
	}

	/**
	 * Calculates the number of remaining available slots for a specific
	 * doctor availability, based on its maximum capacity and the number
	 * of already booked appointments.
	 *
	 * @param availabilityId The ID of the doctor availability.
	 * @return The number of remaining slots. Returns 0 if availability is not found or in case of an error.
	 */
	@Override
	public int getRemainingSlotsForAvailability(String availabilityId) {
		Session session = null;
		int remainingSlots = 0;
		logger.info("Calculating remaining slots for availability ID: " + availabilityId);
		try {
			session = sessionFactory.openSession();

			DoctorAvailability availability = (DoctorAvailability) session.get(DoctorAvailability.class,
					availabilityId);
			if (availability != null) {
				// Count only 'BOOKED' appointments, as 'PENDING' might also occupy a slot
				// depending on business logic. Adjust status check if 'PENDING' slots should also reduce remaining count.
				Long bookedCount = (Long) session.createQuery(
						"select count(*) from Appointment where availability.availability_id = :availabilityId and status = 'BOOKED'")
						.setParameter("availabilityId", availabilityId).uniqueResult();

				remainingSlots = availability.getMax_capacity() - bookedCount.intValue();
				logger.debug("Availability " + availabilityId + ": Max capacity " + availability.getMax_capacity() + ", Booked slots " + bookedCount + ", Remaining " + remainingSlots);
			} else {
				logger.warn("Availability " + availabilityId + " not found when calculating remaining slots. Returning 0.");
			}
		} catch (Exception e) {
			logger.error("Error calculating remaining slots for availability " + availabilityId + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getRemainingSlotsForAvailability operation.");
			}
		}
		return remainingSlots;
	}
}