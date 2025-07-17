package com.infinite.controller;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.servlet.ServletContext;

import com.infinite.dao.AppointmentDaoImpl;
import com.infinite.dao.DoctorDaoImpl;
import com.infinite.dao.RecipientDaoImpl;
import com.infinite.model.Appointment;
import com.infinite.model.AppointmentSlip;
import com.infinite.model.AppointmentStatus;
import com.infinite.model.Doctors;
import com.infinite.model.Recipient;
import com.infinite.util.MailSend;

@ManagedBean
@ViewScoped
public class RecipientAppointmentController implements Serializable {

	private static final long serialVersionUID = 1L;

	private final AppointmentDaoImpl appointmentDao = new AppointmentDaoImpl();

	private String hId = "H1003"; // Ideally from session

	private List<Appointment> upcomingAppointments = new ArrayList<>();
	private List<Appointment> pastAppointments = new ArrayList<>();
	private List<Appointment> filteredAppointments = new ArrayList<>();

	private String timeFilterType = "future"; // future or past
	private String statusFilterType = "ALL"; // ALL, PENDING, BOOKED, CANCELLED, COMPLETED

	private Appointment selectedAppointment;

	private Map<String, Boolean> cancellableMap = new HashMap<>();

	@PostConstruct
	public void init() {
		loadAppointments();
	}

	public void loadAppointments() {
		try {
			upcomingAppointments = appointmentDao.getUpcomingAppointmentsByRecipient(hId);
			pastAppointments = appointmentDao.getPastAppointmentsByRecipient(hId);
			updateFilteredAppointments();
		} catch (Exception e) {
			System.err.println("Error loading appointments: " + e.getMessage());
			upcomingAppointments.clear();
			pastAppointments.clear();
			filteredAppointments.clear();
		}
	}

	public void updateFilteredAppointments() {
		List<Appointment> baseList = "past".equalsIgnoreCase(timeFilterType) ? pastAppointments : upcomingAppointments;

		filteredAppointments = new ArrayList<>();
		cancellableMap.clear();

		for (Appointment appt : baseList) {
			boolean matchStatus = "ALL".equalsIgnoreCase(statusFilterType)
					|| (appt.getStatus() != null && appt.getStatus().name().equalsIgnoreCase(statusFilterType));
			if (matchStatus) {
				filteredAppointments.add(appt);
				cancellableMap.put(appt.getAppointment_id(), isCancellable(appt));
			}
		}
	}

	public boolean isCancellable(Appointment appt) {
		if (appt == null || appt.getStart() == null)
			return false;
		boolean isFuture = appt.getStart().after(new Timestamp(System.currentTimeMillis()));
		return isFuture
				&& (appt.getStatus() == AppointmentStatus.BOOKED || appt.getStatus() == AppointmentStatus.PENDING);
	}

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

	public String cancelAppointment() {
		if (selectedAppointment == null)
			return null;

		try {
			boolean success = appointmentDao.cancelAppointment(selectedAppointment.getAppointment_id());
			if (!success)
				return null;

			Recipient res = new RecipientDaoImpl().searchRecipientById(selectedAppointment.getRecipient().getH_id());
			Doctors doctor = new DoctorDaoImpl().searchADoctorById(selectedAppointment.getDoctor().getDoctor_id());

			ServletContext context = (ServletContext) FacesContext.getCurrentInstance().getExternalContext()
					.getContext();

			String subject = "Appointment Cancelled – Infinite HealthSure";

			String date = selectedAppointment.getStart().toString().split(" ")[0];
			String startTime = selectedAppointment.getStart().toString().split(" ")[1];
			String endTime = selectedAppointment.getEnd().toString().split(" ")[1];

			AppointmentSlip slip = new AppointmentSlip(res.getFirst_name() + " " + res.getLast_name(),
					selectedAppointment.getAppointment_id(), "Infinite HealthSure Hospital",
					context.getInitParameter("providerEmail"), context.getInitParameter("contact"),
					doctor.getDoctor_name(), doctor.getSpecialization(), date, selectedAppointment.getSlot_no(),
					startTime + " - " + endTime);

			try {
				MailSend.sendInfo(res.getEmail(), subject, MailSend.appointmentCancellation(slip));
			} catch (Exception e) {
				System.err.println("Error sending cancellation email: " + e.getMessage());
			}

			loadAppointments(); // Refresh
			return "recipient-appointments?faces-redirect=true";

		} catch (Exception e) {
			System.err.println("Error cancelling appointment: " + e.getMessage());
			return null;
		}
	}

	// ========== Getters & Setters ==========

	public List<Appointment> getFilteredAppointments() {
		return filteredAppointments;
	}

	public String getTimeFilterType() {
		return timeFilterType;
	}

	public void setTimeFilterType(String timeFilterType) {
		this.timeFilterType = timeFilterType;
		updateFilteredAppointments();
	}

	public String getStatusFilterType() {
		return statusFilterType;
	}

	public void setStatusFilterType(String statusFilterType) {
		this.statusFilterType = statusFilterType;
		updateFilteredAppointments();
	}

	public Appointment getSelectedAppointment() {
		return selectedAppointment;
	}

	public void setSelectedAppointment(Appointment selectedAppointment) {
		this.selectedAppointment = selectedAppointment;
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
}
