package com.infinite.controller;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger; // Import Log4j Logger

import com.infinite.dao.DoctorAvailabilityDaoImpl;
import com.infinite.dao.DoctorDaoImpl;
import com.infinite.dao.RecipientDaoImpl;
import com.infinite.dao.AppointmentDaoImpl;
import com.infinite.model.*;
import com.infinite.util.MailSend;

/**
 * `DoctorAvailabilityController` is a JSF managed bean responsible for handling
 * the display and booking of doctor availability slots. It interacts with DAO
 * layers to fetch availability, manage selections, and process appointment bookings.
 */
public class DoctorAvailabilityController implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger logger = Logger.getLogger(DoctorAvailabilityController.class);

	private final DoctorAvailabilityDaoImpl availabilityDao = new DoctorAvailabilityDaoImpl();
	private final AppointmentDaoImpl appointmentDao = new AppointmentDaoImpl();
	private final RecipientDaoImpl recipientDao = new RecipientDaoImpl(); // Initialize RecipientDao
	private final DoctorDaoImpl doctorDao = new DoctorDaoImpl(); // Initialize DoctorDao

	// Hardcoded doctorId for demonstration. In a real application, this would come from user session/selection.
	private String doctorId = "D1003";
	private List<DayAvailabilitySummary> groupedAvailabilityList;
	private Date selectedDate;
	private String selectedDateInput; // For input field binding
	private List<SlotDisplay> availableSlots;
	private Map<Date, List<DoctorAvailability>> dateMap;
	private String selectedAvailabilityId; // Maps to `availability_id`
	private int selectedSlotNumber; // Specific slot number within an availability block
	private Doctors doctor; // The doctor whose availability is being viewed

	private List<String> availabilityTiming; // Stores formatted time ranges of original availability blocks

	/**
	 * Initializes the controller after construction. Loads upcoming availability
	 * for the default doctor.
	 */
	@PostConstruct
	public void init() {
		logger.info("DoctorAvailabilityController initialized.");
		// Load the doctor details once
		this.doctor = doctorDao.searchADoctorById(doctorId);
		if (this.doctor == null) {
			logger.error("Doctor with ID " + doctorId + " not found. Please ensure the doctor exists.");
			// Potentially redirect to an error page or show a prominent message
		}
		loadAllUpcomingAvailability();
	}

	/**
	 * Loads all upcoming doctor availability slots for the selected doctor
	 * from today onwards. It groups these slots by date and calculates
	 * the total remaining slots for each day.
	 */
	public void loadAllUpcomingAvailability() {
		logger.info("Loading all upcoming availability for doctor ID: " + doctorId);
		Date today = new Date(System.currentTimeMillis());
		List<DoctorAvailability> futureSlots = availabilityDao.getUpcomingAvailabilitiesForDoctor(doctorId, today);

		// Group slots by date using a TreeMap to ensure chronological order of dates
		dateMap = futureSlots.stream().collect(
				Collectors.groupingBy(DoctorAvailability::getAvailable_date, TreeMap::new, Collectors.toList()));

		// Create a summary for each day including the total remaining slots
		groupedAvailabilityList = dateMap.entrySet().stream()
				.map(entry -> {
					Date date = entry.getKey();
					List<DoctorAvailability> availabilitiesForDate = entry.getValue();
					int totalRemainingSlots = availabilitiesForDate.stream()
							.mapToInt(da -> availabilityDao.getRemainingSlotsForAvailability(da.getAvailability_id()))
							.sum();
					return new DayAvailabilitySummary(date, formatDisplayDate(date), totalRemainingSlots);
				})
				.collect(Collectors.toList());
		logger.info("Loaded " + groupedAvailabilityList.size() + " days with upcoming availability.");
	}

	/**
	 * Loads individual bookable slots for the currently `selectedDate`.
	 * It calculates slot timings based on the availability block's start/end times
	 * and max capacity, and filters for truly available (unbooked) slots.
	 */
	public void loadAvailableSlots() {
		logger.info("Loading available slots for selected date: " + selectedDate);
		// Reset lists to ensure fresh data on date change
		availabilityTiming = new ArrayList<>();
		availableSlots = new ArrayList<>();

		if (selectedDate == null) {
			logger.warn("No date selected to load available slots.");
			return;
		}

		List<DoctorAvailability> dailyAvailabilities = dateMap.get(selectedDate);
		if (dailyAvailabilities == null || dailyAvailabilities.isEmpty()) {
			logger.info("No availability blocks found for " + selectedDate + " for doctor " + doctorId);
			return; // No availabilities for the selected date
		}

		// First, populate availabilityTiming with original block times
		for (DoctorAvailability a : dailyAvailabilities) {
			availabilityTiming.add(
					a.getStart_time().toString().substring(0, 5) + " - " + a.getEnd_time().toString().substring(0, 5));
		}

		for (DoctorAvailability availability : dailyAvailabilities) {
			String availabilityId = availability.getAvailability_id();

			// Retrieve the slot numbers that are *not yet booked*
			List<Integer> availableSlotNumbers = appointmentDao.getAvailableSlotNumbers(availabilityId);

			if (availableSlotNumbers.isEmpty()) {
				logger.debug("No free slots found for availability ID: " + availabilityId);
				continue; // No free slots in this availability block
			}

			Timestamp startTimestamp = Timestamp.valueOf(availability.getAvailable_date() + " " + availability.getStart_time());
			Timestamp endTimestamp = Timestamp.valueOf(availability.getAvailable_date() + " " + availability.getEnd_time());
			int maxCapacity = availability.getMax_capacity();

			// Calculate duration of each slot in minutes
			long totalDurationMinutes = (endTimestamp.getTime() - startTimestamp.getTime()) / (60 * 1000);
			if (maxCapacity == 0) {
				logger.warn("Max capacity is 0 for availability " + availabilityId + ". Cannot calculate slot duration.");
				continue;
			}
			long slotDurationMinutes = totalDurationMinutes / maxCapacity;

			if (slotDurationMinutes <= 0) {
				logger.warn("Calculated slot duration is non-positive for availability " + availabilityId + ". Check start/end times and max capacity.");
				continue;
			}

			// Generate SlotDisplay objects for each available slot number
			for (Integer slotNo : availableSlotNumbers) {
				long slotStartMillis = startTimestamp.getTime() + (slotNo - 1) * slotDurationMinutes * 60 * 1000;
				LocalTime slotStartTime = new Timestamp(slotStartMillis).toLocalDateTime().toLocalTime();
				LocalTime slotEndTime = new Timestamp(slotStartMillis + slotDurationMinutes * 60 * 1000).toLocalDateTime()
						.toLocalTime();

				availableSlots.add(
						new SlotDisplay(availabilityId, slotNo, formatTime(slotStartTime), formatTime(slotEndTime)));
			}
		}
		logger.info("Loaded " + availableSlots.size() + " individual available slots for " + selectedDate);
	}


	/**
	 * Handles the selection of a date from the input field. It attempts to parse
	 * the input string into a `java.sql.Date` and then reloads the available slots
	 * for that date.
	 */
	public void handleDateSelection() {
		logger.info("Handling date selection. Input: " + selectedDateInput);
		FacesContext context = FacesContext.getCurrentInstance();
		try {
			if (selectedDateInput != null && !selectedDateInput.isEmpty()) {
				// Convert String to java.sql.Date (assuming YYYY-MM-DD format)
				selectedDate = Date.valueOf(selectedDateInput);
				loadAvailableSlots();
			} else {
				// Clear previously selected date and slots if input is empty
				selectedDate = null;
				availableSlots = new ArrayList<>();
				availabilityTiming = new ArrayList<>();
				logger.debug("Selected date input is empty, clearing selection.");
			}
		} catch (IllegalArgumentException e) {
			logger.error("Invalid date format entered: " + selectedDateInput, e);
			context.addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid date format. Please use YYYY-MM-DD", null));
			// Keep selectedDateInput to allow user to correct
		} catch (Exception e) {
			logger.error("An unexpected error occurred during date selection: " + e.getMessage(), e);
			context.addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "An unexpected error occurred.", null));
		}
	}

	/**
	 * Initiates the appointment booking process. It validates the selected
	 * availability, creates an `Appointment` object, persists it to the database,
	 * and sends a confirmation email to the patient.
	 *
	 * @return A navigation outcome string. Returns "appointmentConfirmation?faces-redirect=true"
	 * on successful booking, or `null` if the booking fails or an error occurs.
	 */
	public String bookAppointment() {
		logger.info("Attempting to book an appointment. Availability ID: " + selectedAvailabilityId + ", Slot Number: " + selectedSlotNumber);
		FacesContext context = FacesContext.getCurrentInstance();

		if (selectedAvailabilityId == null || selectedSlotNumber == 0) {
			context.addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_WARN, "Please select a time slot to book.", null));
			return null;
		}

		try {
			DoctorAvailability availability = availabilityDao.getAvailabilityById(selectedAvailabilityId);

			if (availability == null) {
				context.addMessage(null,
						new FacesMessage(FacesMessage.SEVERITY_ERROR, "Selected time slot is no longer available or invalid.", null));
				resetSelection();
				loadAvailableSlots(); // Refresh to reflect latest state
				return null;
			}

			// Perform a final check to ensure the slot is still available just before booking
			List<Integer> currentlyAvailableSlots = appointmentDao.getAvailableSlotNumbers(selectedAvailabilityId);
			if (!currentlyAvailableSlots.contains(selectedSlotNumber)) {
				context.addMessage(null,
						new FacesMessage(FacesMessage.SEVERITY_ERROR, "The selected slot has just been booked by another user. Please choose another.", null));
				resetSelection();
				loadAvailableSlots(); // Refresh to reflect latest state
				return null;
			}

			Appointment appointment = new Appointment();
			appointment.setAvailability(availability);
			appointment.setDoctor(availability.getDoctor()); // Doctor from availability object
			appointment.setSlot_no(selectedSlotNumber);

			// --- Hardcoded Recipient and Provider for demonstration ---
			// In a real application, the recipient ID would come from the logged-in user's session.
			// The provider would come from the doctor's associated provider.
			Recipient recipient = recipientDao.searchRecipientById("H1003"); // Assuming H1003 is logged in
			if (recipient == null) {
				logger.error("Recipient with ID H1003 not found. Cannot book appointment.");
				context.addMessage(null,
						new FacesMessage(FacesMessage.SEVERITY_ERROR, "User information not found. Please log in again.", null));
				return null;
			}
			appointment.setRecipient(recipient);

			Providers provider = availability.getDoctor().getProvider(); // Get provider from the doctor linked to availability
			if (provider == null) {
				logger.error("Provider information missing for doctor " + doctor.getDoctor_id() + ". Cannot book appointment.");
				context.addMessage(null,
						new FacesMessage(FacesMessage.SEVERITY_ERROR, "Provider information unavailable for this doctor.", null));
				return null;
			}
			appointment.setProvider(provider);
			// --- End Hardcoded ---

			String result = appointmentDao.bookAnAppointment(appointment);

			if (result != null && result.startsWith("Appointment booked")) {
				HttpSession session = (HttpSession) FacesContext.getCurrentInstance().getExternalContext()
						.getSession(true);
				session.setAttribute("confirmationMessage", result);

				String newAppointmentId = result.split(" ")[result.split(" ").length - 1];
				Appointment bookedAppointment = appointmentDao.getAppointmentById(newAppointmentId);

				if (bookedAppointment == null) {
					logger.error("Booked appointment with ID " + newAppointmentId + " not found immediately after booking. Mail may not be sent.");
					context.addMessage(null,
							new FacesMessage(FacesMessage.SEVERITY_WARN, "Appointment booked, but confirmation details could not be retrieved.", null));
				} else {
					// Prepare data for email
					ServletContext servletContext = (ServletContext) FacesContext.getCurrentInstance().getExternalContext()
							.getContext();

					String patientFullName = recipient.getFirst_name() + " " + recipient.getLast_name();
					String doctorName = bookedAppointment.getDoctor().getDoctor_name();
					String doctorSpecialization = bookedAppointment.getDoctor().getSpecialization();
					String providerName = bookedAppointment.getProvider().getProvider_name();
					String providerEmail = servletContext.getInitParameter("providerEmail"); // From web.xml context-param
					String providerContact = servletContext.getInitParameter("contact"); // From web.xml context-param

					// Ensure these timestamps are correctly formatted for display in email
					String appointmentDate = new SimpleDateFormat("yyyy-MM-dd").format(bookedAppointment.getStart());
					String appointmentStartTime = new SimpleDateFormat("HH:mm").format(bookedAppointment.getStart());
					String appointmentEndTime = new SimpleDateFormat("HH:mm").format(bookedAppointment.getEnd());
					int slotNumber = bookedAppointment.getSlot_no();

					AppointmentSlip apSli = new AppointmentSlip(patientFullName, newAppointmentId, providerName,
							providerEmail, providerContact, doctorName, doctorSpecialization,
							appointmentDate, slotNumber, appointmentStartTime + " - " + appointmentEndTime);

					String subject = "Appointment Request Received – Awaiting Confirmation";
					try {
						String mailResult = MailSend.sendInfo(recipient.getEmail(), subject, MailSend.appointmentRequest(apSli));
						logger.info("Email sent to " + recipient.getEmail() + ": " + mailResult);
					} catch (Exception e) {
						logger.error("Error sending appointment request email to " + recipient.getEmail(), e);
						System.out.println("Error while sending the mail here: " + e.getMessage()); // For immediate feedback
					}
				}

				resetSelection();
				loadAllUpcomingAvailability(); // Reload all to update slot counts
				loadAvailableSlots(); // Ensure current date's slots are refreshed
				return "appointmentConfirmation?faces-redirect=true";
			} else {
				context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, result, null));
				resetSelection();
				loadAvailableSlots(); // Refresh available slots
			}
		} catch (Exception e) {
			logger.error("An unexpected error occurred during appointment booking: " + e.getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error booking appointment: " + e.getMessage(), null));
		}
		return null;
	}

	/**
	 * Resets the selected availability ID, slot number, and date inputs.
	 */
	private void resetSelection() {
		this.selectedAvailabilityId = null;
		this.selectedSlotNumber = 0;
		this.selectedDateInput = null;
		this.selectedDate = null;
		this.availableSlots = new ArrayList<>(); // Clear displayed slots
		this.availabilityTiming = new ArrayList<>(); // Clear displayed timings
		logger.debug("Appointment selection reset.");
	}

	/**
	 * Filters the `availableSlots` list to include only slots occurring in the morning (up to noon).
	 *
	 * @return A list of `SlotDisplay` objects for morning slots.
	 */
	public List<SlotDisplay> getMorningSlots() {
		return filterSlotsByTime(LocalTime.MIN, LocalTime.NOON);
	}

	/**
	 * Filters the `availableSlots` list to include only slots occurring in the afternoon (noon to 5 PM).
	 *
	 * @return A list of `SlotDisplay` objects for afternoon slots.
	 */
	public List<SlotDisplay> getAfternoonSlots() {
		return filterSlotsByTime(LocalTime.NOON, LocalTime.of(17, 0));
	}

	/**
	 * Filters the `availableSlots` list to include only slots occurring in the evening (5 PM onwards).
	 *
	 * @return A list of `SlotDisplay` objects for evening slots.
	 */
	public List<SlotDisplay> getEveningSlots() {
		return filterSlotsByTime(LocalTime.of(17, 0), LocalTime.MAX);
	}

	/**
	 * Helper method to filter a list of `SlotDisplay` objects based on a time range.
	 *
	 * @param start The start `LocalTime` (inclusive).
	 * @param end   The end `LocalTime` (exclusive).
	 * @return A filtered list of `SlotDisplay` objects.
	 */
	private List<SlotDisplay> filterSlotsByTime(LocalTime start, LocalTime end) {
		if (availableSlots == null || availableSlots.isEmpty()) {
			return Collections.emptyList();
		}

		return availableSlots.stream().filter(slot -> {
			try {
				LocalTime time = LocalTime.parse(slot.getStartTime());
				return !time.isBefore(start) && time.isBefore(end);
			} catch (java.time.format.DateTimeParseException e) {
				logger.error("Failed to parse slot start time: " + slot.getStartTime() + " for filtering.", e);
				return false; // Exclude malformed entries
			}
		}).collect(Collectors.toList());
	}

	/**
	 * Formats a `java.sql.Date` into a display-friendly string (e.g., "Mon 15, Jul").
	 *
	 * @param date The `java.sql.Date` to format.
	 * @return A formatted date string.
	 */
	private String formatDisplayDate(Date date) {
		if (date == null) {
			return "";
		}
		return new SimpleDateFormat("E d, MMM", Locale.ENGLISH).format(date);
	}

	/**
	 * Formats a `LocalTime` into a "HH:mm" string.
	 *
	 * @param time The `LocalTime` to format.
	 * @return A formatted time string.
	 */
	private String formatTime(LocalTime time) {
		if (time == null) {
			return "";
		}
		return String.format("%02d:%02d", time.getHour(), time.getMinute());
	}

	// Getters and Setters for JSF binding

	public List<DayAvailabilitySummary> getGroupedAvailabilityList() {
		return groupedAvailabilityList;
	}

	public Date getSelectedDate() {
		return selectedDate;
	}

	public void setSelectedDate(Date selectedDate) {
		this.selectedDate = selectedDate;
	}

	public String getSelectedDateInput() {
		return selectedDateInput;
	}

	public void setSelectedDateInput(String selectedDateInput) {
		this.selectedDateInput = selectedDateInput;
	}

	public List<SlotDisplay> getAvailableSlots() {
		return availableSlots;
	}

	public int getMorningSlotCount() {
		return getMorningSlots().size();
	}

	public int getAfternoonSlotCount() {
		return getAfternoonSlots().size();
	}

	public int getEveningSlotCount() {
		return getEveningSlots().size();
	}

	public String getSelectedAvailabilityId() {
		return selectedAvailabilityId;
	}

	public void setSelectedAvailabilityId(String selectedAvailabilityId) {
		this.selectedAvailabilityId = selectedAvailabilityId;
	}

	public int getSelectedSlotNumber() {
		return selectedSlotNumber;
	}

	public void setSelectedSlotNumber(int selectedSlotNumber) {
		this.selectedSlotNumber = selectedSlotNumber;
	}

	public Doctors getDoctor() {
		return doctor;
	}

	public void setDoctor(Doctors doctor) {
		this.doctor = doctor;
	}

	public List<String> getAvailabilityTiming() {
		return availabilityTiming;
	}

	public void setAvailabilityTiming(List<String> availabilityTiming) {
		this.availabilityTiming = availabilityTiming;
	}

	// Inner classes for data display

	/**
	 * `DayAvailabilitySummary` is an inner class used to summarize availability
	 * for a specific date, including the total number of remaining slots.
	 */
	public static class DayAvailabilitySummary implements Serializable {
		private static final long serialVersionUID = 1L;
		private final Date date;
		private final String displayDate;
		private final int totalSlots;

		public DayAvailabilitySummary(Date date, String displayDate, int totalSlots) {
			this.date = date;
			this.displayDate = displayDate;
			this.totalSlots = totalSlots;
		}

		public Date getDate() {
			return date;
		}

		public String getDisplayDate() {
			return displayDate;
		}

		public int getTotalSlots() {
			return totalSlots;
		}

		@Override
		public String toString() {
			return "DayAvailabilitySummary{" +
					"date=" + date +
					", displayDate='" + displayDate + '\'' +
					", totalSlots=" + totalSlots +
					'}';
		}
	}

	/**
	 * `SlotDisplay` is an inner class used to represent an individual bookable
	 * time slot, including its associated availability ID, slot number,
	 * and formatted start and end times.
	 */
	public static class SlotDisplay implements Serializable {
		private static final long serialVersionUID = 1L;
		private final String availabilityId;
		private final int slotNumber;
		private final String startTime;
		private final String endTime;

		public SlotDisplay(String availabilityId, int slotNumber, String startTime, String endTime) {
			this.availabilityId = availabilityId;
			this.slotNumber = slotNumber;
			this.startTime = startTime;
			this.endTime = endTime;
		}

		public String getAvailabilityId() {
			return availabilityId;
		}

		public int getSlotNumber() {
			return slotNumber;
		}

		public String getStartTime() {
			return startTime;
		}

		public String getEndTime() {
			return endTime;
		}

		/**
		 * Returns a formatted time range string (e.g., "09:00 - 09:30").
		 *
		 * @return The formatted time range.
		 */
		public String getFormattedTimeRange() {
			return startTime + " - " + endTime;
		}

		@Override
		public String toString() {
			return "SlotDisplay{" +
					"availabilityId='" + availabilityId + '\'' +
					", slotNumber=" + slotNumber +
					", startTime='" + startTime + '\'' +
					", endTime='" + endTime + '\'' +
					'}';
		}
	}
}