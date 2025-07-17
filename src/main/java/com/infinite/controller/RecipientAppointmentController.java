package com.infinite.controller;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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

	private String hId = "H1003"; // from session ideally

	private List<Appointment> upcomingAppointments = new ArrayList<>();
	private List<Appointment> pastAppointments = new ArrayList<>();
	private List<Appointment> filteredAppointments = new ArrayList<>();

	private String timeFilterType = "future"; // future or past
	private String statusFilterType = "ALL"; // ALL, PENDING, BOOKED, CANCELLED, COMPLETED

	private Appointment selectedAppointment;
	

	public List<SelectItem> getStatusFilterOptions() {
		List<SelectItem> options = new ArrayList<>();
		options.add(new SelectItem("ALL", "All"));
		options.add(new SelectItem("PENDING", "Pending"));
		options.add(new SelectItem("BOOKED", "Booked"));
		options.add(new SelectItem("CANCELLED", "Cancelled"));

		// Only add 'COMPLETED' if viewing past appointments
		if ("past".equalsIgnoreCase(timeFilterType)) {
			options.add(new SelectItem("COMPLETED", "Completed"));
		}
		return options;
	}


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

		if ("ALL".equalsIgnoreCase(statusFilterType)) {
			filteredAppointments = new ArrayList<>(baseList);
		} else {
			filteredAppointments = new ArrayList<>();
			for (Appointment appt : baseList) {
				if (appt.getStatus() != null && appt.getStatus().name().equalsIgnoreCase(statusFilterType)) {
					filteredAppointments.add(appt);
				}
			}
		}
	}

	public String cancelAppointment() {
		if (selectedAppointment != null) {
			try {
				boolean success = appointmentDao.cancelAppointment(selectedAppointment.getAppointment_id());

				Recipient res = new RecipientDaoImpl()
						.searchRecipientById(selectedAppointment.getRecipient().getH_id());

				if (success) {
					Doctors doctor = new DoctorDaoImpl()
							.searchADoctorById(selectedAppointment.getDoctor().getDoctor_id());

					ServletContext servletContext = (ServletContext) FacesContext.getCurrentInstance()
							.getExternalContext().getContext();

					String subject = "Appointment Cancelled – Infinite HealthSure";

					AppointmentSlip slip = new AppointmentSlip(res.getFirst_name() + " " + res.getLast_name(),
							selectedAppointment.getAppointment_id(), "Infinite HealthSure Hospital",
							servletContext.getInitParameter("providerEmail"),
							servletContext.getInitParameter("contact"), doctor.getDoctor_name(),
							doctor.getSpecialization(), selectedAppointment.getStart().toString().split(" ")[0],
							selectedAppointment.getSlot_no(), selectedAppointment.getStart().toString().split(" ")[1]
									+ " - " + selectedAppointment.getEnd().toString().split(" ")[1]);

					try {
						MailSend.sendInfo(res.getEmail(), subject, MailSend.appointmentCancellation(slip));
					} catch (Exception e) {
						System.err.println("Error sending cancellation email: " + e.getMessage());
					}

					loadAppointments(); // reload with updates
					return "recipient-appointments?faces-redirect=true";
				}
			} catch (Exception e) {
				System.err.println("Error canceling appointment: " + e.getMessage());
			}
		}
		return null;
	}

	// ====================== GETTERS & SETTERS =======================

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
}
