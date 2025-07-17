package com.infinite.dao;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger; // Import Log4j Logger
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;

import com.infinite.model.Appointment;
import com.infinite.model.AppointmentStatus;
import com.infinite.model.DoctorAvailability;
import com.infinite.model.DoctorStatus;
import com.infinite.model.RecipientStatus;
import com.infinite.util.SessionHelper;

/**
 * `AppointmentDaoImpl` provides the concrete implementation for managing
 * appointment-related data operations in the database. It includes methods for
 * booking, cancelling, updating, and querying appointments, along with various
 * validation checks.
 */
public class AppointmentDaoImpl implements AppointmentDao {

	private static final Logger logger = Logger.getLogger(AppointmentDaoImpl.class);

	/**
	 * Generates the next unique appointment ID in "APPT###" format. It queries the
	 * database for the last used ID and increments the numeric part. If no previous
	 * ID is found or parsing fails, it defaults to "APPT101".
	 *
	 * @param session The Hibernate session to use for database interaction.
	 * @return The newly generated unique appointment ID.
	 */
	private String generateNextAppointmentId(Session session) {
		final String prefix = "APPT";
		String hql = "SELECT a.appointment_id FROM Appointment a ORDER BY a.appointment_id DESC";
		Query query = session.createQuery(hql);
		query.setMaxResults(1);
		String lastId = (String) query.uniqueResult();
		logger.debug("Last appointment ID found: " + lastId);

		int nextNumber = 101; // default start
		if (lastId != null && lastId.startsWith(prefix)) {
			try {
				int lastNumber = Integer.parseInt(lastId.substring(prefix.length()));
				nextNumber = lastNumber + 1;
				logger.debug("Next appointment number calculated: " + nextNumber);
			} catch (NumberFormatException e) {
				logger.warn("Could not parse last appointment ID '" + lastId + "', defaulting to " + nextNumber, e);
				// fallback to default 101 already set
			}
		}
		String generatedId = prefix + nextNumber;
		logger.info("Generated new appointment ID: " + generatedId);
		return generatedId;
	}

	/**
	 * Books a new appointment after performing a series of comprehensive
	 * validations. Validations include checking for past dates, doctor/recipient
	 * status, overlapping appointments for both recipient and doctor, slot
	 * availability, capacity limits, number of upcoming appointments for the
	 * recipient, minimum notice period, and working hours.
	 *
	 * @param appointment The `Appointment` object containing details to be booked.
	 * @return A String message indicating the success or failure of the booking,
	 *         along with specific reasons for failure.
	 */
	@Override
	public String bookAnAppointment(Appointment appointment) {
		Transaction tx = null;
		String result = null;
		Session session = null;
		logger.info("Attempting to book a new appointment.");

		try {
			// 1. Load the availability details
			DoctorAvailabilityDaoImpl availabilityDao = new DoctorAvailabilityDaoImpl();
			DoctorAvailability doctoravail = availabilityDao
					.getAvailabilityById(appointment.getAvailability().getAvailability_id());

			if (doctoravail == null) {
				logger.warn("Booking failed: Invalid availability slot ID "
						+ appointment.getAvailability().getAvailability_id());
				return "Invalid availability slot. Please select a valid time slot.";
			}
			logger.debug("Availability details loaded for ID: " + doctoravail.getAvailability_id());

			// 2. Set calculated start and end times for the appointment
			appointment.setAvailability(doctoravail);
			// Calculate the exact start time of the selected slot
			long slotSt = Timestamp
					.valueOf(doctoravail.getStart_time().toLocalTime()
							.atDate(doctoravail.getAvailable_date().toLocalDate()))
					.getTime() + (long) (appointment.getSlot_no() - 1) * doctoravail.getPatient_window() * 60 * 1000;
			// Calculate the exact end time of the selected slot
			long slotEn = slotSt + (long) doctoravail.getPatient_window() * 60 * 1000;
			appointment.setStart(new Timestamp(slotSt));
			appointment.setEnd(new Timestamp(slotEn));
			logger.debug("Calculated appointment start: " + appointment.getStart() + ", end: " + appointment.getEnd());

			session = SessionHelper.getSessionFactory().openSession();
			tx = session.beginTransaction();

			String availabilityId = appointment.getAvailability().getAvailability_id();
			String recipientId = appointment.getRecipient().getH_id();
			String doctorId = appointment.getDoctor().getDoctor_id();
			int slotNo = appointment.getSlot_no();
			Timestamp now = new Timestamp(System.currentTimeMillis());
			LocalDateTime currentLocalDateTime = LocalDateTime.now();

			// VALIDATION 1: Prevent booking in the past
			if (appointment.getStart().before(now)) {
				logger.warn("Booking failed for recipient " + recipientId
						+ ": Cannot book an appointment in the past. Appointment start: " + appointment.getStart());
				return "Cannot book an appointment in the past.";
			}

			// VALIDATION 2: Check if doctor is active
			Query doctorStatusQuery = session
					.createQuery("SELECT d.doctor_status FROM Doctors d WHERE d.doctor_id = :doctorId");
			doctorStatusQuery.setParameter("doctorId", doctorId);
			DoctorStatus doctorStatus = (DoctorStatus) doctorStatusQuery.uniqueResult();

			if (doctorStatus == null || !"ACTIVE".equals(doctorStatus.name())) {
				logger.warn("Booking failed for recipient " + recipientId + ": Doctor " + doctorId
						+ " is not active. Status: " + (doctorStatus != null ? doctorStatus.name() : "N/A"));
				return "Doctor is not currently active. Please select another doctor.";
			}
			logger.debug("Doctor " + doctorId + " is active.");

			// VALIDATION 3: Check if recipient is active
			Query recipientStatusQuery = session
					.createQuery("SELECT r.status FROM Recipient r WHERE r.h_id = :recipientId");
			recipientStatusQuery.setParameter("recipientId", recipientId);
			RecipientStatus recipientStatus = (RecipientStatus) recipientStatusQuery.uniqueResult();

			if (recipientStatus == null || !"ACTIVE".equals(recipientStatus.name())) {
				logger.warn(
						"Booking failed for recipient " + recipientId + ": Recipient account is not active. Status: "
								+ (recipientStatus != null ? recipientStatus.name() : "N/A"));
				return "Your account is not active. Please contact support.";
			}
			logger.debug("Recipient " + recipientId + " is active.");

			// VALIDATION 4: Check if recipient already has overlapping appointment
			Query overlapQuery = session.createQuery(
					"FROM Appointment a WHERE a.recipient.h_id = :recipientId AND a.status IN ('BOOKED', 'PENDING') "
							+ "AND ((a.start < :endTime AND a.end > :startTime) OR "
							+ "(a.start = :startTime AND a.end = :endTime))");
			overlapQuery.setParameter("recipientId", recipientId);
			overlapQuery.setParameter("startTime", appointment.getStart());
			overlapQuery.setParameter("endTime", appointment.getEnd());

			if (!overlapQuery.list().isEmpty()) {
				logger.warn("Booking failed for recipient " + recipientId + ": Overlapping appointment found.");
				return "You already have an appointment scheduled during this time.";
			}
			logger.debug("No overlapping appointments found for recipient " + recipientId + ".");

			// VALIDATION 5: Check if the slot number is already booked in this availability
			Query slotQuery = session
					.createQuery("FROM Appointment WHERE availability.availability_id = :availabilityId "
							+ "AND slot_no = :slotNo AND status IN ('BOOKED', 'PENDING')");
			slotQuery.setParameter("availabilityId", availabilityId);
			slotQuery.setParameter("slotNo", slotNo);

			if (!slotQuery.list().isEmpty()) {
				logger.warn("Booking failed: Slot " + slotNo + " for availability " + availabilityId
						+ " is already booked.");
				return "This time slot is already booked. Please choose another time.";
			}
			logger.debug("Slot " + slotNo + " for availability " + availabilityId + " is available.");

			// VALIDATION 6: Check if max capacity is reached for this availability
			Query bookedCountQuery = session.createQuery(
					"SELECT COUNT(*) FROM Appointment WHERE availability.availability_id = :availabilityId "
							+ "AND status IN ('BOOKED', 'PENDING')");
			bookedCountQuery.setParameter("availabilityId", availabilityId);
			long bookedCount = (Long) bookedCountQuery.uniqueResult();

			int maxCapacity = appointment.getAvailability().getMax_capacity();
			if (bookedCount >= maxCapacity) {
				logger.warn("Booking failed: Max capacity reached for availability " + availabilityId + ". Booked: "
						+ bookedCount + ", Max: " + maxCapacity);
				return "All slots for this availability are already full.";
			}
			logger.debug("Availability " + availabilityId + " has capacity. Booked: " + bookedCount + ", Max: "
					+ maxCapacity);

			// VALIDATION 7: Check if recipient already has 10 upcoming appointments
			Query upcomingQuery = session
					.createQuery("SELECT COUNT(*) FROM Appointment WHERE recipient.h_id = :recipientId "
							+ "AND status IN ('BOOKED', 'PENDING') AND start > :now");
			upcomingQuery.setParameter("recipientId", recipientId);
			upcomingQuery.setParameter("now", now);

			long upcomingCount = (Long) upcomingQuery.uniqueResult();
			if (upcomingCount >= 10) {
				logger.warn("Booking failed for recipient " + recipientId
						+ ": Exceeded maximum upcoming appointments (10). Current: " + upcomingCount);
				return "You can only have 10 upcoming appointments at a time.";
			}
			logger.debug(
					"Recipient " + recipientId + " has " + upcomingCount + " upcoming appointments (within limit).");

			// VALIDATION 8: Check if recipient has any pending appointments with same
			// doctor
			Query pendingWithDoctorQuery = session
					.createQuery("FROM Appointment a WHERE a.recipient.h_id = :recipientId "
							+ "AND a.doctor.doctor_id = :doctorId AND a.status = 'PENDING' AND a.start > :currentTime");
			pendingWithDoctorQuery.setParameter("recipientId", recipientId);
			pendingWithDoctorQuery.setParameter("doctorId", doctorId);
			pendingWithDoctorQuery.setParameter("currentTime", new Timestamp(System.currentTimeMillis()));

			List<Appointment> pendingAppointments = pendingWithDoctorQuery.list();
			if (!pendingAppointments.isEmpty()) {
				Appointment existingAppointment = pendingAppointments.get(0);
				DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
				DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

				String formattedDate = existingAppointment.getStart().toLocalDateTime().format(dateFormatter);
				String formattedTime = existingAppointment.getStart().toLocalDateTime().format(timeFormatter);
				logger.warn("Booking failed for recipient " + recipientId
						+ ": Already has a pending appointment with doctor " + doctorId + " on " + formattedDate
						+ " at " + formattedTime);
				return "You already have a pending appointment with this doctor on " + formattedDate + " at "
						+ formattedTime + ". Please complete or cancel that appointment first.";
			}
			logger.debug(
					"No pending appointments with doctor " + doctorId + " found for recipient " + recipientId + ".");

			// VALIDATION 9: Check if slot number is within valid range
			if (slotNo < 1 || slotNo > maxCapacity) {
				logger.warn("Booking failed: Invalid slot number " + slotNo + " for availability " + availabilityId
						+ " (Max: " + maxCapacity + ")");
				return "Invalid slot number. Please select a valid slot.";
			}
			logger.debug("Slot number " + slotNo + " is within valid range.");

			// VALIDATION 10: Check if availability date is in the future
			if (doctoravail.getAvailable_date().before(Date.valueOf(LocalDate.now()))) {
				logger.warn("Booking failed: Attempted to book for a past date " + doctoravail.getAvailable_date());
				return "Cannot book appointments for past dates.";
			}
			logger.debug("Availability date " + doctoravail.getAvailable_date() + " is in the future.");

			// VALIDATION 11: Check if doctor has any scheduling conflicts
			Query doctorOverlapQuery = session.createQuery("FROM Appointment a WHERE a.doctor.doctor_id = :doctorId "
					+ "AND a.status IN ('BOOKED', 'PENDING') " + "AND ((a.start < :endTime AND a.end > :startTime))");
			doctorOverlapQuery.setParameter("doctorId", doctorId);
			doctorOverlapQuery.setParameter("startTime", appointment.getStart());
			doctorOverlapQuery.setParameter("endTime", appointment.getEnd());

			if (!doctorOverlapQuery.list().isEmpty()) {
				logger.warn("Booking failed for doctor " + doctorId + ": Doctor has a scheduling conflict during "
						+ appointment.getStart() + " - " + appointment.getEnd());
				return "Doctor has a scheduling conflict during this time.";
			}
			logger.debug("No scheduling conflicts found for doctor " + doctorId + ".");

			// VALIDATION 12: Check if the appointment is too far in the future (e.g., 6
			// months)
			LocalDate maxFutureDate = LocalDate.now().plusMonths(6);
			if (doctoravail.getAvailable_date().after(Date.valueOf(maxFutureDate))) {
				logger.warn("Booking failed: Appointment date " + doctoravail.getAvailable_date()
						+ " is more than 6 months in advance.");
				return "Appointments can only be booked up to 6 months in advance.";
			}
			logger.debug("Appointment date is within 6 months future limit.");

			// VALIDATION 13: Check if the appointment is within working hours (8:00 AM to
			// 8:00 PM)
			if (appointment.getStart().toLocalDateTime().toLocalTime().isBefore(LocalTime.of(8, 0))
					|| appointment.getEnd().toLocalDateTime().toLocalTime().isAfter(LocalTime.of(20, 0))) {
				logger.warn("Booking failed: Appointment time " + appointment.getStart().toLocalDateTime().toLocalTime()
						+ " - " + appointment.getEnd().toLocalDateTime().toLocalTime()
						+ " is outside working hours (8 AM - 8 PM).");
				return "Appointments must be between 8:00 AM and 8:00 PM.";
			}
			logger.debug("Appointment time is within working hours.");

			// VALIDATION 14: Check minimum notice period (e.g., 2 hours before appointment)
			LocalDateTime minNoticeTime = currentLocalDateTime.plusHours(2);
			if (appointment.getStart().toLocalDateTime().isBefore(minNoticeTime)) {
				logger.warn("Booking failed: Appointment requires at least 2 hours notice. Current time: "
						+ currentLocalDateTime + ", Appointment start: " + appointment.getStart().toLocalDateTime());
				return "Appointments must be booked at least 2 hours in advance.";
			}
			logger.debug("Appointment meets minimum 2-hour notice period.");

			// All validations passed - save the appointment
			appointment.setRequested_at(now);
			appointment.setStatus(AppointmentStatus.PENDING);
			appointment.setAppointment_id(generateNextAppointmentId(session)); // Generate ID using the same session

			session.save(appointment);
			tx.commit();
			logger.info("Appointment booked successfully with ID: " + appointment.getAppointment_id()
					+ " for recipient " + recipientId);
			result = "Appointment booked successfully with ID: " + appointment.getAppointment_id();
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
				logger.error("Transaction rolled back due to error during appointment booking.", e);
			}
			logger.error("Error booking appointment: " + e.getMessage(), e);
			result = "Error booking appointment: " + e.getMessage();
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after booking operation.");
			}
		}
		return result;
	}

	/**
	 * Checks if a specific doctor availability slot is completely full based on the
	 * number of booked/pending appointments versus its maximum capacity.
	 *
	 * @param availabilityId The ID of the doctor availability to check.
	 * @return true if the slot is full, false otherwise or if availability is not
	 *         found.
	 */
	@Override
	public boolean isAvailabilitySlotFull(String availabilityId) {
		logger.info("Checking if availability slot " + availabilityId + " is full.");
		Session session = null;
		try {
			session = SessionHelper.getSessionFactory().openSession();
			// Step 1: Get total booked/pending appointments for the availability
			Query countQuery = session.createQuery(
					"SELECT COUNT(*) FROM Appointment WHERE availability.availability_id = :availabilityId "
							+ "AND status IN ('BOOKED', 'PENDING')");
			countQuery.setParameter("availabilityId", availabilityId);
			long bookedCount = (Long) countQuery.uniqueResult();
			logger.debug("Booked/pending count for availability " + availabilityId + ": " + bookedCount);

			// Step 2: Get max capacity from DoctorAvailability
			Query capacityQuery = session.createQuery(
					"SELECT a.max_capacity FROM DoctorAvailability a WHERE a.availability_id = :availabilityId");
			capacityQuery.setParameter("availabilityId", availabilityId);
			Integer maxCapacity = (Integer) capacityQuery.uniqueResult();

			if (maxCapacity == null) {
				logger.warn("Availability " + availabilityId
						+ " not found while checking if slot is full. Returning false.");
				return false; // availability not found, assume not full
			}
			logger.debug("Max capacity for availability " + availabilityId + ": " + maxCapacity);

			boolean isFull = bookedCount >= maxCapacity;
			logger.info("Availability slot " + availabilityId + " is full: " + isFull);
			return isFull;
		} catch (Exception e) {
			logger.error("Error checking if availability slot " + availabilityId + " is full: " + e.getMessage(), e);
			return false; // on error, assume not full
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after isAvailabilitySlotFull operation.");
			}
		}
	}

	/**
	 * Retrieves a list of upcoming appointments for a given recipient. Upcoming
	 * appointments are defined as those with a status of BOOKED, PENDING, or
	 * CANCELLED, and whose start time is in the future relative to the current
	 * time.
	 *
	 * @param recipientId The ID of the recipient.
	 * @return A list of `Appointment` objects, sorted by start time in ascending
	 *         order. Returns an empty list if no upcoming appointments are found or
	 *         in case of an error.
	 */
	@Override
	public List<Appointment> getUpcomingAppointmentsByRecipient(String recipientId) {
		logger.info("Fetching upcoming appointments for recipient ID: " + recipientId);
		Session session = null;
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Timestamp now = new Timestamp(System.currentTimeMillis());

			Query query = session.createQuery("FROM Appointment a WHERE a.recipient.h_id = :recipientId "
					+ "AND a.status IN ('BOOKED', 'PENDING', 'CANCELLED') "
					+ "AND a.start > :now ORDER BY a.start ASC");

			query.setParameter("recipientId", recipientId);
			query.setParameter("now", now);

			@SuppressWarnings("unchecked")
			List<Appointment> list = query.list();
			logger.info("Found " + list.size() + " upcoming appointments for recipient " + recipientId + ".");
			return list;
		} catch (Exception e) {
			logger.error("Error fetching upcoming appointments for recipient " + recipientId + ": " + e.getMessage(),
					e);
			return new ArrayList<>(); // Return empty list on error
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getUpcomingAppointmentsByRecipient operation.");
			}
		}
	}

	/**
	 * Retrieves a list of past appointments for a given recipient. Past
	 * appointments are defined as those whose start time is before the current
	 * time, regardless of their status.
	 *
	 * @param recipientId The ID of the recipient.
	 * @return A list of `Appointment` objects, sorted by start time in descending
	 *         order. Returns an empty list if no past appointments are found or in
	 *         case of an error.
	 */
	@Override
	public List<Appointment> getPastAppointmentsByRecipient(String recipientId) {
		logger.info("Fetching past appointments for recipient ID: " + recipientId);
		Session session = null;
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Timestamp now = new Timestamp(System.currentTimeMillis());

			Query query = session.createQuery("FROM Appointment a WHERE a.recipient.h_id = :recipientId "
					+ "AND a.start < :now ORDER BY a.start DESC");
			query.setParameter("recipientId", recipientId);
			query.setParameter("now", now);

			@SuppressWarnings("unchecked")
			List<Appointment> list = query.list();
			logger.info("Found " + list.size() + " past appointments for recipient " + recipientId + ".");
			return list;
		} catch (Exception e) {
			logger.error("Error fetching past appointments for recipient " + recipientId + ": " + e.getMessage(), e);
			return new ArrayList<>(); // Return empty list on error
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getPastAppointmentsByRecipient operation.");
			}
		}
	}

	/**
	 * Retrieves a single appointment by its unique ID.
	 *
	 * @param appointmentId The unique ID of the appointment.
	 * @return The `Appointment` object if found, otherwise null.
	 */
	@Override
	public Appointment getAppointmentById(String appointmentId) {
		logger.info("Attempting to retrieve appointment by ID: " + appointmentId);
		Session session = null;
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Appointment appointment = (Appointment) session.get(Appointment.class, appointmentId);
			if (appointment != null) {
				logger.debug("Appointment " + appointmentId + " found.");
			} else {
				logger.warn("Appointment with ID " + appointmentId + " not found.");
			}
			return appointment;
		} catch (Exception e) {
			logger.error("Error retrieving appointment by ID " + appointmentId + ": " + e.getMessage(), e);
			return null;
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAppointmentById operation.");
			}
		}
	}

	/**
	 * Cancels a specific appointment. An appointment can only be cancelled if its
	 * start time is in the future. The `status` of the appointment is updated to
	 * `CANCELLED` and `cancelled_at` timestamp is set.
	 *
	 * @param appointmentId The ID of the appointment to cancel.
	 * @return true if the appointment was successfully cancelled, false otherwise
	 *         (e.g., appointment not found, or it's a past appointment).
	 */
	@Override
	public boolean cancelAppointment(String appointmentId) {
		Transaction tx = null;
		Session session = null;
		logger.info("Attempting to cancel appointment with ID: " + appointmentId);
		try {
			session = SessionHelper.getSessionFactory().openSession();
			tx = session.beginTransaction();

			Appointment appointment = (Appointment) session.get(Appointment.class, appointmentId);
			if (appointment == null) {
				logger.warn("Cancellation failed: Appointment with ID " + appointmentId + " not found.");
				return false; // No such appointment
			}

			// Check if appointment is in the future
			Timestamp now = new Timestamp(System.currentTimeMillis());
			if (appointment.getStart() != null && appointment.getStart().before(now)) {
				logger.warn("Cancellation failed for appointment " + appointmentId
						+ ": Appointment is in the past (Start: " + appointment.getStart() + ").");
				return false; // Past appointment can't be cancelled
			}

			appointment.setStatus(AppointmentStatus.CANCELLED);
			appointment.setCancelled_at(now);

			session.update(appointment);
			tx.commit();
			logger.info("Appointment " + appointmentId + " successfully cancelled.");
			return true;
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
				logger.error("Transaction rolled back due to error during appointment cancellation.", e);
			}
			logger.error("Error cancelling appointment " + appointmentId + ": " + e.getMessage(), e);
			return false;
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after cancelAppointment operation.");
			}
		}
	}

	/**
	 * Updates an existing appointment with new details. It performs several
	 * validations similar to booking, ensuring the updated appointment does not
	 * conflict with existing ones for the recipient or doctor, and that the
	 * original appointment is in the future.
	 *
	 * @param updatedAppointment The `Appointment` object containing the updated
	 *                           details.
	 * @return true if the appointment was successfully updated, false otherwise.
	 */
	@Override
	public boolean updateAppointment(Appointment updatedAppointment) {
		Transaction tx = null;
		Session session = null;
		logger.info("Attempting to update appointment with ID: " + updatedAppointment.getAppointment_id());
		try {
			session = SessionHelper.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Load the original appointment
			Appointment existing = (Appointment) session.get(Appointment.class, updatedAppointment.getAppointment_id());
			if (existing == null) {
				logger.warn("Update failed: Original appointment with ID " + updatedAppointment.getAppointment_id()
						+ " not found.");
				return false;
			}
			logger.debug("Original appointment " + existing.getAppointment_id() + " loaded for update.");

			// Allow update only if appointment is in the future
			Timestamp now = new Timestamp(System.currentTimeMillis());
			if (existing.getStart() != null && existing.getStart().before(now)) {
				logger.warn("Update failed for appointment " + existing.getAppointment_id()
						+ ": Cannot update past appointment (Start: " + existing.getStart() + ").");
				return false; // Cannot update past appointment
			}

			String availabilityId = updatedAppointment.getAvailability().getAvailability_id();
			String recipientId = updatedAppointment.getRecipient().getH_id();
			int slotNo = updatedAppointment.getSlot_no();

			// Check if new slot overlaps with another existing appointment of recipient
			Query overlapQuery = session.createQuery(
					"FROM Appointment a WHERE a.recipient.h_id = :recipientId AND a.status IN ('BOOKED', 'PENDING') "
							+ "AND ((a.start <= :endTime AND a.end >= :startTime)) AND a.appointment_id != :currentId");
			overlapQuery.setParameter("recipientId", recipientId);
			overlapQuery.setParameter("startTime", updatedAppointment.getStart());
			overlapQuery.setParameter("endTime", updatedAppointment.getEnd());
			overlapQuery.setParameter("currentId", updatedAppointment.getAppointment_id());

			if (!overlapQuery.list().isEmpty()) {
				logger.warn("Update failed for appointment " + updatedAppointment.getAppointment_id() + ": Recipient "
						+ recipientId + " has an overlapping appointment.");
				return false; // Overlapping found
			}
			logger.debug("No overlapping appointments found for recipient " + recipientId + " for update.");

			// Check if the new slot number is already taken in same availability
			Query slotQuery = session.createQuery(
					"FROM Appointment a WHERE a.availability.availability_id = :availabilityId AND a.slot_no = :slotNo "
							+ "AND a.status IN ('BOOKED', 'PENDING') AND a.appointment_id != :currentId");
			slotQuery.setParameter("availabilityId", availabilityId);
			slotQuery.setParameter("slotNo", slotNo);
			slotQuery.setParameter("currentId", updatedAppointment.getAppointment_id());

			if (!slotQuery.list().isEmpty()) {
				logger.warn("Update failed for appointment " + updatedAppointment.getAppointment_id() + ": Slot "
						+ slotNo + " in availability " + availabilityId + " is already taken.");
				return false; // Slot taken
			}
			logger.debug("New slot " + slotNo + " for availability " + availabilityId + " is available for update.");

			// Update details of the existing appointment object
			existing.setAvailability(updatedAppointment.getAvailability());
			existing.setSlot_no(slotNo);
			existing.setStart(updatedAppointment.getStart());
			existing.setEnd(updatedAppointment.getEnd());
			existing.setNotes(updatedAppointment.getNotes());
			// Status and booked_at, requested_at usually not updated here unless specific
			// logic allows

			session.update(existing);
			tx.commit();
			logger.info("Appointment " + updatedAppointment.getAppointment_id() + " successfully updated.");
			return true;
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
				logger.error("Transaction rolled back due to error during appointment update.", e);
			}
			logger.error("Error updating appointment " + updatedAppointment.getAppointment_id() + ": " + e.getMessage(),
					e);
			return false;
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after updateAppointment operation.");
			}
		}
	}

	/**
	 * Retrieves the count of booked or pending appointments for a given
	 * availability slot. This helps in determining remaining capacity for an
	 * availability.
	 *
	 * @param availabilityId The ID of the doctor availability slot.
	 * @return The number of appointments currently booked or pending for the slot.
	 *         Returns 0 if no appointments are found or in case of an error.
	 */
	@Override
	public int getBookedCountForAvailability(String availabilityId) {
		int count = 0;
		Session session = null;
		logger.info("Getting booked count for availability ID: " + availabilityId);
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Query query = session.createQuery(
					"SELECT COUNT(*) FROM Appointment a " + "WHERE a.availability.availability_id = :availabilityId "
							+ "AND a.status IN ('BOOKED', 'PENDING')");
			query.setParameter("availabilityId", availabilityId);
			Long result = (Long) query.uniqueResult();
			count = result != null ? result.intValue() : 0;
			logger.debug("Booked/pending count for " + availabilityId + " is: " + count);
		} catch (Exception e) {
			logger.error("Error getting booked count for availability " + availabilityId + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getBookedCountForAvailability operation.");
			}
		}
		return count;
	}

	/**
	 * Placeholder method. Current implementation always returns false. This method
	 * is intended to check for overlapping appointments for a recipient based on a
	 * given availability ID, start time, and end time.
	 *
	 * @param recipientId    The ID of the recipient.
	 * @param availabilityId The ID of the doctor availability.
	 * @param start          The proposed start time of the appointment.
	 * @param end            The proposed end time of the appointment.
	 * @return Always returns false in the current implementation.
	 */
	@Override
	public boolean hasOverlappingAppointment(String recipientId, String availabilityId, Timestamp start,
			Timestamp end) {
		logger.warn(
				"Method hasOverlappingAppointment is a placeholder and always returns false. Review implementation.");
		// TODO: Implement actual logic for checking overlapping appointments
		return false;
	}

	/**
	 * Retrieves a list of available slot numbers for a given doctor availability.
	 * It determines available slots by comparing the maximum capacity of the
	 * availability with currently booked or pending slots.
	 *
	 * @param availabilityId The ID of the doctor availability.
	 * @return A list of integer representing the available slot numbers. Returns an
	 *         empty list if no slots are available, availability not found, or in
	 *         case of error.
	 */
	@Override
	public List<Integer> getAvailableSlotNumbers(String availabilityId) {
		List<Integer> availableSlots = new ArrayList<>();
		Session session = null;
		logger.info("Getting available slot numbers for availability ID: " + availabilityId);
		try {
			session = SessionHelper.getSessionFactory().openSession();
			// Step 1: Get max_capacity from DoctorAvailability
			Query capacityQuery = session.createQuery(
					"SELECT da.max_capacity FROM DoctorAvailability da WHERE da.availability_id = :availabilityId");
			capacityQuery.setParameter("availabilityId", availabilityId);
			Integer maxCapacity = (Integer) capacityQuery.uniqueResult();

			if (maxCapacity == null || maxCapacity <= 0) {
				logger.warn("Availability " + availabilityId + " not found or has invalid max capacity (" + maxCapacity
						+ "). Returning empty list.");
				return availableSlots; // return empty list if invalid
			}
			logger.debug("Max capacity for " + availabilityId + ": " + maxCapacity);

			// Step 2: Get all booked slot numbers
			Query bookedQuery = session.createQuery(
					"SELECT a.slot_no FROM Appointment a " + "WHERE a.availability.availability_id = :availabilityId "
							+ "AND a.status IN ('BOOKED', 'PENDING')");
			bookedQuery.setParameter("availabilityId", availabilityId);
			@SuppressWarnings("unchecked")
			List<Integer> bookedSlots = bookedQuery.list();
			logger.debug("Booked slots for " + availabilityId + ": " + bookedSlots);

			// Step 3: Prepare the full range and subtract booked slots
			for (int i = 1; i <= maxCapacity; i++) {
				if (!bookedSlots.contains(i)) {
					availableSlots.add(i);
				}
			}
			logger.info("Found " + availableSlots.size() + " available slots for " + availabilityId + ": "
					+ availableSlots);
		} catch (Exception e) {
			logger.error(
					"Error getting available slot numbers for availability " + availabilityId + ": " + e.getMessage(),
					e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAvailableSlotNumbers operation.");
			}
		}
		return availableSlots;
	}

	/**
	 * Checks if a specific slot number within a given availability is already
	 * booked or is in pending status.
	 *
	 * @param availabilityId The ID of the doctor availability.
	 * @param slotNo         The slot number to check.
	 * @return true if the slot is booked or pending, false otherwise.
	 */
	@Override
	public boolean isSlotAlreadyBooked(String availabilityId, int slotNo) {
		Session session = null;
		logger.info("Checking if slot " + slotNo + " for availability " + availabilityId + " is already booked.");
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Query query = session.createQuery(
					"SELECT count(*) FROM Appointment a " + "WHERE a.availability.availability_id = :availabilityId "
							+ "AND a.slot_no = :slotNo AND a.status IN ('BOOKED', 'PENDING')");
			query.setParameter("availabilityId", availabilityId);
			query.setParameter("slotNo", slotNo);

			Long count = (Long) query.uniqueResult();
			boolean isBooked = count != null && count > 0;
			logger.debug("Slot " + slotNo + " for availability " + availabilityId + " is booked: " + isBooked);
			return isBooked;
		} catch (Exception e) {
			logger.error("Error checking if slot " + slotNo + " for availability " + availabilityId
					+ " is already booked: " + e.getMessage(), e);
			return false; // return false on error to avoid false positives
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after isSlotAlreadyBooked operation.");
			}
		}
	}

	/**
	 * Retrieves all appointments associated with a specific doctor availability ID.
	 * Appointments are ordered by slot number in ascending order.
	 *
	 * @param availabilityId The ID of the doctor availability.
	 * @return A list of `Appointment` objects for the given availability. Returns
	 *         an empty list if no appointments are found or in case of an error.
	 */
	@Override
	public List<Appointment> getAppointmentsByAvailability(String availabilityId) {
		List<Appointment> appointments = new ArrayList<>();
		Session session = null;
		logger.info("Fetching appointments by availability ID: " + availabilityId);
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Query query = session.createQuery(
					"FROM Appointment a WHERE a.availability.availability_id = :availabilityId ORDER BY a.slot_no ASC");
			query.setParameter("availabilityId", availabilityId);

			@SuppressWarnings("unchecked")
			List<Appointment> list = query.list();
			appointments.addAll(list);
			logger.info("Found " + appointments.size() + " appointments for availability " + availabilityId + ".");
		} catch (Exception e) {
			logger.error("Error getting appointments by availability " + availabilityId + ": " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAppointmentsByAvailability operation.");
			}
		}
		return appointments;
	}

	/**
	 * Checks if a given appointment, identified by its ID, has a start time that is
	 * in the past relative to the current system time.
	 *
	 * @param appointmentId The ID of the appointment to check.
	 * @return true if the appointment's start time is in the past, false otherwise
	 *         (e.g., in the future, appointment not found, or start time is null).
	 */
	@Override
	public boolean isAppointmentInPast(String appointmentId) {
		Session session = null;
		logger.info("Checking if appointment " + appointmentId + " is in the past.");
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Appointment appointment = (Appointment) session.get(Appointment.class, appointmentId);

			if (appointment != null && appointment.getStart() != null) {
				Timestamp now = new Timestamp(System.currentTimeMillis());
				boolean isInPast = appointment.getStart().before(now);
				logger.debug("Appointment " + appointmentId + " start (" + appointment.getStart() + ") is in the past: "
						+ isInPast);
				return isInPast;
			} else {
				logger.warn("Appointment " + appointmentId + " not found or its start time is null.");
			}
		} catch (Exception e) {
			logger.error("Error checking if appointment " + appointmentId + " is in past: " + e.getMessage(), e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after isAppointmentInPast operation.");
			}
		}
		return false;
	}

	/**
	 * Retrieves a list of appointments for a specific doctor on a given date.
	 *
	 * @param doctorId The ID of the doctor.
	 * @param date     The specific date to search for appointments.
	 * @return A list of `Appointment` objects for the specified doctor and date.
	 *         Returns an empty list if no appointments are found or in case of an
	 *         error.
	 */
	@Override
	public List<Appointment> getAppointmentsByDoctorAndDate(String doctorId, Date date) {
		List<Appointment> appointments = new ArrayList<>();
		Session session = null;
		logger.info("Fetching appointments for doctor " + doctorId + " on date " + date);
		try {
			session = SessionHelper.getSessionFactory().openSession();
			Query query = session.createQuery(
					"FROM Appointment a WHERE a.doctor.doctor_id = :doctorId AND DATE(a.start) = :appointmentDate");
			query.setParameter("doctorId", doctorId);
			query.setParameter("appointmentDate", date);

			@SuppressWarnings("unchecked")
			List<Appointment> list = query.list();
			appointments.addAll(list);
			logger.info(
					"Found " + appointments.size() + " appointments for doctor " + doctorId + " on date " + date + ".");
		} catch (Exception e) {
			logger.error(
					"Error getting appointments for doctor " + doctorId + " on date " + date + ": " + e.getMessage(),
					e);
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after getAppointmentsByDoctorAndDate operation.");
			}
		}
		return appointments;
	}

	/**
	 * Checks if a calculated slot time for a given availability ID and slot number
	 * is in the future. This is useful for front-end validation to prevent users
	 * from attempting to book past slots.
	 *
	 * @param availabilityId The ID of the doctor availability.
	 * @param slotNo         The slot number within that availability.
	 * @return true if the calculated slot start time is in the future, false
	 *         otherwise.
	 */
	@Override
	public boolean isSlotTimeInFuture(String availabilityId, int slotNo) {
		Session session = null;
		logger.info("Checking if slot " + slotNo + " for availability " + availabilityId + " is in the future.");
		try {
			session = SessionHelper.getSessionFactory().openSession();
			DoctorAvailability availability = (DoctorAvailability) session.get(DoctorAvailability.class,
					availabilityId);
			if (availability == null) {
				logger.warn("Availability " + availabilityId + " not found. Cannot check if slot time is in future.");
				return false;
			}

			// Calculate slot duration
			int windowMinutes = availability.getPatient_window();
			if (windowMinutes <= 0) {
				logger.warn("Invalid patient window (" + windowMinutes + ") for availability " + availabilityId
						+ ". Cannot check if slot time is in future.");
				return false;
			}

			// Calculate the full Timestamp for the availability's start date and time
			java.sql.Time startTime = availability.getStart_time();
			Timestamp availableDateTime = Timestamp
					.valueOf(availability.getAvailable_date().toString() + " " + startTime.toString());

			// Calculate the start time of the specific slot
			long slotStartMillis = availableDateTime.getTime() + (long) (slotNo - 1) * windowMinutes * 60 * 1000L;
			Timestamp slotStart = new Timestamp(slotStartMillis);

			// Compare with current time
			boolean isInFuture = slotStart.after(new Timestamp(System.currentTimeMillis()));
			logger.debug("Calculated slot start time for " + availabilityId + " slot " + slotNo + ": " + slotStart
					+ ". Is in future: " + isInFuture);
			return isInFuture;
		} catch (Exception e) {
			logger.error("Error checking if slot time for availability " + availabilityId + " slot " + slotNo
					+ " is in future: " + e.getMessage(), e);
			return false;
		} finally {
			if (session != null) {
				session.close();
				logger.debug("Hibernate session closed after isSlotTimeInFuture operation.");
			}
		}
	}
}