package com.infinite.controller;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.servlet.ServletContext;

import org.apache.log4j.Logger; // Import Log4j Logger

import com.infinite.dao.AppointmentDaoImpl;
import com.infinite.dao.DoctorDaoImpl;
import com.infinite.dao.RecipientDaoImpl;
import com.infinite.model.Appointment;
import com.infinite.model.AppointmentSlip;
import com.infinite.model.AppointmentStatus;
import com.infinite.model.Doctors;
import com.infinite.model.Recipient;
import com.infinite.util.MailSend;

/**
 * `RecipientAppointmentController` is a JSF Managed Bean responsible for
 * managing and displaying appointments for a recipient (patient). It handles
 * filtering, pagination, and cancellation of appointments.
 */
@ManagedBean // Marks this class as a JSF Managed Bean
@ViewScoped // Specifies the bean's scope (lives as long as the user stays on the same view)
public class RecipientAppointmentController implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = Logger.getLogger(RecipientAppointmentController.class);

	private final AppointmentDaoImpl appointmentDao = new AppointmentDaoImpl();
	private final RecipientDaoImpl recipientDao = new RecipientDaoImpl(); // Added for recipient details
	private final DoctorDaoImpl doctorDao = new DoctorDaoImpl(); // Added for doctor details

	private String hId = "H1003"; // TODO: Ideally from session/logged-in user context
	private Recipient currentRecipient; // To store recipient details once fetched

	private List<Appointment> upcomingAppointments = new ArrayList<>();
	private List<Appointment> pastAppointments = new ArrayList<>();
	private List<Appointment> filteredAppointments = new ArrayList<>();
	private List<Appointment> paginatedAppointments = new ArrayList<>();

	// Map to store cancellability status for each appointment by ID
	private Map<String, Boolean> cancellableMap = new HashMap<>();

	private Appointment selectedAppointment; // For details view or cancellation

	private String timeFilterType = "future"; // "future" or "past"
	private String statusFilterType = "ALL"; // ALL, PENDING, BOOKED, CANCELLED, COMPLETED

	// Pagination properties
	private int pageSize = 5; // Default number of items per page
	private int currentPage = 1; // Current page number (1-based)
	private int totalPages = 1; // Total number of pages

	/**
	 * Initializes the controller. This method is called after the bean is
	 * constructed. It loads the current recipient's details and all their
	 * appointments.
	 */
	@PostConstruct
	public void init() {
		logger.info("RecipientAppointmentController initialized for recipient ID: " + hId);
		currentRecipient = recipientDao.searchRecipientById(hId);
		if (currentRecipient == null) {
			logger.error("Recipient with ID " + hId
					+ " not found. Please ensure the recipient exists or the ID is correct.");
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_FATAL,
					"User data could not be loaded. Please contact support.", null));
			// Prevent further operations if recipient is not found
			return;
		}
		loadAppointments();
	}

	/**
	 * Loads upcoming and past appointments for the current recipient from the
	 * database. After loading, it triggers an update to filter and paginate the
	 * appointments.
	 */
	public void loadAppointments() {
		logger.info("Loading appointments for recipient ID: " + hId);
		try {
			// Fetch raw lists from DAO
			upcomingAppointments = appointmentDao.getUpcomingAppointmentsByRecipient(hId);
			pastAppointments = appointmentDao.getPastAppointmentsByRecipient(hId);
			logger.info("Fetched " + upcomingAppointments.size() + " upcoming and " + pastAppointments.size()
					+ " past appointments.");
			updateFilteredAppointments(); // Apply current filters and pagination
		} catch (Exception e) {
			logger.error("Error loading appointments for recipient " + hId + ": " + e.getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
					"Error loading appointments. Please try again.", null));
			// Clear lists and reset pagination in case of error
			upcomingAppointments.clear();
			pastAppointments.clear();
			filteredAppointments.clear();
			paginatedAppointments.clear();
			totalPages = 1;
			currentPage = 1;
		}
	}

	/**
	 * Updates the `filteredAppointments` list based on the current `timeFilterType`
	 * and `statusFilterType`. It then recalculates pagination parameters and
	 * updates the `paginatedAppointments` list.
	 */
	public void updateFilteredAppointments() {
		logger.info("Updating filtered appointments. Time Filter: " + timeFilterType + ", Status Filter: "
				+ statusFilterType);
		List<Appointment> baseList = "past".equalsIgnoreCase(timeFilterType) ? pastAppointments : upcomingAppointments;

		filteredAppointments = baseList.stream()
				.filter(appt -> "ALL".equalsIgnoreCase(statusFilterType)
						|| (appt.getStatus() != null && appt.getStatus().name().equalsIgnoreCase(statusFilterType)))
				.collect(Collectors.toList());

		// Re-evaluate cancellability for the newly filtered list
		cancellableMap.clear();
		for (Appointment appt : filteredAppointments) {
			cancellableMap.put(appt.getAppointment_id(), isCancellable(appt));
		}

		// Recalculate totalPages based on the *new* filtered list size
		if (filteredAppointments.isEmpty()) {
			totalPages = 1; // If no items after filtering, there's still 1 "empty" page
		} else {
			totalPages = (int) Math.ceil((double) filteredAppointments.size() / pageSize);
		}

		// Adjust currentPage if it's now out of bounds for the new totalPages
		if (currentPage > totalPages) {
			currentPage = totalPages;
		}
		if (currentPage < 1 && totalPages >= 1) { // Ensure current page is never less than 1 (if pages exist)
			currentPage = 1;
		} else if (currentPage < 1 && totalPages == 0) { // Edge case: if totalPages somehow becomes 0
			currentPage = 1; // Default to 1
		}
		logger.debug("Filtered appointments count: " + filteredAppointments.size() + ", Total pages: " + totalPages
				+ ", Current page: " + currentPage);

		updatePaginatedAppointments(); // Update the viewable page
	}

	/**
	 * Updates the `paginatedAppointments` list to reflect the current page and page
	 * size settings from the `filteredAppointments`.
	 */
	public void updatePaginatedAppointments() {
		int fromIndex = (currentPage - 1) * pageSize;
		int toIndex = Math.min(fromIndex + pageSize, filteredAppointments.size());

		if (fromIndex < 0 || fromIndex >= filteredAppointments.size()) {
			// If fromIndex is out of bounds (e.g., after filtering dramatically reduces
			// list)
			paginatedAppointments = new ArrayList<>();
		} else {
			// Ensure toIndex does not go below fromIndex
			if (toIndex < fromIndex) {
				toIndex = fromIndex;
			}
			paginatedAppointments = filteredAppointments.subList(fromIndex, toIndex);
		}
		logger.debug("Paginated appointments count: " + paginatedAppointments.size() + " (fromIndex: " + fromIndex
				+ ", toIndex: " + toIndex + ")");
	}

	/**
	 * Navigates to the next page of appointments, if available.
	 */
	public void nextPage() {
		if (currentPage < totalPages) {
			currentPage++;
			updatePaginatedAppointments();
			logger.info("Navigated to next page: " + currentPage);
		} else {
			logger.debug("Already on last page, cannot navigate to next.");
		}
	}

	/**
	 * Navigates to the previous page of appointments, if available.
	 */
	public void prevPage() {
		if (currentPage > 1) {
			currentPage--;
			updatePaginatedAppointments();
			logger.info("Navigated to previous page: " + currentPage);
		} else {
			logger.debug("Already on first page, cannot navigate to previous.");
		}
	}

	/**
	 * Determines if a given appointment is cancellable. An appointment is
	 * cancellable if its start time is in the future and its status is 'BOOKED' or
	 * 'PENDING'.
	 *
	 * @param appt The `Appointment` object to check.
	 * @return `true` if the appointment can be cancelled, `false` otherwise.
	 */
	public boolean isCancellable(Appointment appt) {
		if (appt == null || appt.getStart() == null || appt.getStatus() == null) {
			return false;
		}
		// Check if the appointment start time is after the current time
		boolean isFuture = appt.getStart().after(new Timestamp(System.currentTimeMillis()));
		// Check if the status allows cancellation
		boolean isBookedOrPending = (appt.getStatus() == AppointmentStatus.BOOKED
				|| appt.getStatus() == AppointmentStatus.PENDING);
		return isFuture && isBookedOrPending;
	}

	/**
	 * Provides options for the status filter dropdown menu. Includes "All",
	 * "Pending", "Booked", "Cancelled", and "Completed" (if `timeFilterType` is
	 * "past").
	 *
	 * @return A `List` of `SelectItem` objects for the status filter.
	 */
	public List<SelectItem> getStatusFilterOptions() {
		List<SelectItem> options = new ArrayList<>();
		options.add(new SelectItem("ALL", "All"));
		options.add(new SelectItem("PENDING", "Pending"));
		options.add(new SelectItem("BOOKED", "Booked"));
		options.add(new SelectItem("CANCELLED", "Cancelled"));
		if ("past".equalsIgnoreCase(timeFilterType)) {
			options.add(new SelectItem("COMPLETED", "Completed"));
		}
		return options;
	}

	/**
	 * Handles the cancellation of the `selectedAppointment`. It attempts to update
	 * the appointment status in the database and sends a cancellation email to the
	 * recipient.
	 *
	 * @return A navigation outcome string. Returns
	 *         "recipient-appointments?faces-redirect=true" on success, or `null` if
	 *         the cancellation fails.
	 */
	public String cancelAppointment() {
		if (selectedAppointment == null) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_WARN, "No appointment selected for cancellation.", null));
			logger.warn("Attempted to cancel appointment without a selection.");
			return null;
		}

		// Re-check cancellability just before proceeding with cancellation
		if (!isCancellable(selectedAppointment)) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
					"This appointment cannot be cancelled at this time.", null));
			logger.warn(
					"Attempted to cancel a non-cancellable appointment: " + selectedAppointment.getAppointment_id());
			return null;
		}

		logger.info("Attempting to cancel appointment ID: " + selectedAppointment.getAppointment_id());

		try {
			boolean success = appointmentDao.cancelAppointment(selectedAppointment.getAppointment_id());
			if (!success) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
						"Failed to cancel appointment in the database. Please try again.", null));
				logger.error(
						"Database cancellation failed for appointment ID: " + selectedAppointment.getAppointment_id());
				return null;
			}

			// Fetch fresh recipient and doctor data in case it's needed for email and not
			// fully loaded in selectedAppointment
			Recipient recipient = recipientDao.searchRecipientById(selectedAppointment.getRecipient().getH_id());
			Doctors doctor = doctorDao.searchADoctorById(selectedAppointment.getDoctor().getDoctor_id());

			if (recipient == null) {
				logger.error("Recipient not found for email sending during cancellation for appointment: "
						+ selectedAppointment.getAppointment_id());
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
						"Appointment cancelled, but recipient details for email not found.", null));
				loadAppointments(); // Still refresh data
				return "recipient-appointments?faces-redirect=true";
			}
			if (doctor == null) {
				logger.error("Doctor not found for email sending during cancellation for appointment: "
						+ selectedAppointment.getAppointment_id());
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
						"Appointment cancelled, but doctor details for email not found.", null));
				loadAppointments(); // Still refresh data
				return "recipient-appointments?faces-redirect=true";
			}

			ServletContext context = (ServletContext) FacesContext.getCurrentInstance().getExternalContext()
					.getContext();

			String subject = "Appointment Cancelled – Infinite HealthSure";

			// Safely format date and time for email slip
			String date = (selectedAppointment.getStart() != null)
					? new SimpleDateFormat("yyyy-MM-dd").format(selectedAppointment.getStart())
					: "N/A";
			String startTime = (selectedAppointment.getStart() != null)
					? new SimpleDateFormat("HH:mm").format(selectedAppointment.getStart())
					: "N/A";
			String endTime = (selectedAppointment.getEnd() != null)
					? new SimpleDateFormat("HH:mm").format(selectedAppointment.getEnd())
					: "N/A";

			AppointmentSlip slip = new AppointmentSlip(recipient.getFirst_name() + " " + recipient.getLast_name(),
					selectedAppointment.getAppointment_id(), selectedAppointment.getProvider().getProvider_name(), // Use
																													// actual
																													// provider
																													// name
																													// from
																													// appointment
					context.getInitParameter("providerEmail"), context.getInitParameter("contact"),
					doctor.getDoctor_name(), doctor.getSpecialization(), date, selectedAppointment.getSlot_no(),
					startTime + " - " + endTime);

			try {
				String mailResult = MailSend.sendInfo(recipient.getEmail(), subject,
						MailSend.appointmentCancellation(slip));
				logger.info("Cancellation email sent to " + recipient.getEmail() + ": " + mailResult);
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
						"Appointment cancelled successfully. A confirmation email has been sent.", null));
			} catch (Exception emailEx) {
				logger.error("Error sending cancellation email for appointment "
						+ selectedAppointment.getAppointment_id() + ": " + emailEx.getMessage(), emailEx);
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
						"Appointment cancelled, but failed to send confirmation email.", null));
			}

			loadAppointments(); // Refresh data to reflect the cancellation and re-apply filters/pagination
			selectedAppointment = null; // Clear selection after action
			return "recipient-appointments?faces-redirect=true"; // Navigate back to the same page

		} catch (Exception e) {
			logger.error("An unexpected error occurred during cancellation of appointment "
					+ selectedAppointment.getAppointment_id() + ": " + e.getMessage(), e);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
					"An unexpected error occurred during cancellation.", null));
			return null;
		}
	}

	// ======================= GETTERS & SETTERS ========================

	public List<Appointment> getPaginatedAppointments() {
		return paginatedAppointments;
	}

	public String getTimeFilterType() {
		return timeFilterType;
	}

	public void setTimeFilterType(String timeFilterType) {
		this.timeFilterType = timeFilterType;
		this.currentPage = 1; // Reset to first page on filter change
		updateFilteredAppointments();
	}

	public String getStatusFilterType() {
		return statusFilterType;
	}

	public void setStatusFilterType(String statusFilterType) {
		this.statusFilterType = statusFilterType;
		this.currentPage = 1; // Reset to first page on filter change
		updateFilteredAppointments();
	}

	public Appointment getSelectedAppointment() {
		return selectedAppointment;
	}

	public void setSelectedAppointment(Appointment selectedAppointment) {
		this.selectedAppointment = selectedAppointment;
		logger.debug("Selected Appointment ID: "
				+ (selectedAppointment != null ? selectedAppointment.getAppointment_id() : "null"));
	}

	public String getHId() {
		return hId;
	}

	public void setHId(String hId) {
		this.hId = hId;
	}

	public Map<String, Boolean> getCancellableMap() {
		return cancellableMap;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public int getTotalPages() {
		return totalPages;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		// Ensure pageSize is always positive
		if (pageSize <= 0) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
					"Page size must be a positive number. Setting to default 5.", null));
			this.pageSize = 5; // Fallback to default
		} else {
			this.pageSize = pageSize;
		}
		this.currentPage = 1; // Reset to first page if page size changes
		updateFilteredAppointments();
		logger.info("Page size set to: " + this.pageSize);
	}

	public boolean isShowPagination() {
		return filteredAppointments != null && filteredAppointments.size() > pageSize;
	}
}

