package com.infinite.controller;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
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

    private String hId = "H1003"; // Ideally from session, make sure this is correctly populated
                                 // e.g., from a login session attribute.

    private List<Appointment> upcomingAppointments = new ArrayList<>();
    private List<Appointment> pastAppointments = new ArrayList<>();
    private List<Appointment> filteredAppointments = new ArrayList<>();
    private List<Appointment> paginatedAppointments = new ArrayList<>();

    private Map<String, Boolean> cancellableMap = new HashMap<>();

    private Appointment selectedAppointment;

    private String timeFilterType = "future"; // "future" or "past"
    private String statusFilterType = "ALL"; // ALL, PENDING, BOOKED, CANCELLED, COMPLETED

    // Pagination
    private int pageSize = 5;
    private int currentPage = 1;
    private int totalPages = 1;

    @PostConstruct
    public void init() {
        loadAppointments();
    }

    public void loadAppointments() {
        try {
            // Ensure these DAO calls return consistent data
            upcomingAppointments = appointmentDao.getUpcomingAppointmentsByRecipient(hId);
            pastAppointments = appointmentDao.getPastAppointmentsByRecipient(hId);
            updateFilteredAppointments();
        } catch (Exception e) {
            System.err.println("Error loading appointments: " + e.getMessage());
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Error loading appointments. Please try again.", null));
            upcomingAppointments.clear();
            pastAppointments.clear();
            filteredAppointments.clear();
            paginatedAppointments.clear();
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

        // Re-calculate totalPages and adjust currentPage if necessary
        totalPages = (int) Math.ceil((double) filteredAppointments.size() / pageSize);
        if (totalPages == 0) {
            totalPages = 1; // Always have at least one page
        }
        if (currentPage > totalPages) {
            currentPage = totalPages; // Adjust current page if it's out of bounds
        }
        if (currentPage < 1 && totalPages >=1 ) { // Ensure current page is at least 1
             currentPage = 1;
        }

        updatePaginatedAppointments();
    }

    public void updatePaginatedAppointments() {
        int fromIndex = (currentPage - 1) * pageSize;
        // Ensure toIndex does not exceed the list size
        int toIndex = Math.min(fromIndex + pageSize, filteredAppointments.size());

        if (fromIndex >= filteredAppointments.size()) {
            // If fromIndex is beyond the list size (e.g., after filtering removes all items on current page)
            paginatedAppointments = new ArrayList<>();
        } else {
            paginatedAppointments = filteredAppointments.subList(fromIndex, toIndex);
        }
    }

    public void nextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            updatePaginatedAppointments();
        }
    }

    public void prevPage() {
        if (currentPage > 1) {
            currentPage--;
            updatePaginatedAppointments();
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
        if (selectedAppointment == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
                    "No appointment selected for cancellation.", null));
            return null;
        }

        try {
            boolean success = appointmentDao.cancelAppointment(selectedAppointment.getAppointment_id());
            if (!success) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Failed to cancel appointment in the database.", null));
                return null;
            }

            Recipient recipient = new RecipientDaoImpl()
                    .searchRecipientById(selectedAppointment.getRecipient().getH_id());
            Doctors doctor = new DoctorDaoImpl().searchADoctorById(selectedAppointment.getDoctor().getDoctor_id());

            ServletContext context = (ServletContext) FacesContext.getCurrentInstance().getExternalContext()
                    .getContext();

            String subject = "Appointment Cancelled – Infinite HealthSure";

            // Safely format date and time
            String date = (selectedAppointment.getStart() != null) ? selectedAppointment.getStart().toString().split(" ")[0] : "N/A";
            String startTime = (selectedAppointment.getStart() != null) ? selectedAppointment.getStart().toString().split(" ")[1] : "N/A";
            String endTime = (selectedAppointment.getEnd() != null) ? selectedAppointment.getEnd().toString().split(" ")[1] : "N/A";

            AppointmentSlip slip = new AppointmentSlip(recipient.getFirst_name() + " " + recipient.getLast_name(),
                    selectedAppointment.getAppointment_id(), "Infinite HealthSure Hospital",
                    context.getInitParameter("providerEmail"), context.getInitParameter("contact"),
                    doctor.getDoctor_name(), doctor.getSpecialization(), date, selectedAppointment.getSlot_no(),
                    startTime + " - " + endTime);

            try {
                MailSend.sendInfo(recipient.getEmail(), subject, MailSend.appointmentCancellation(slip));
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
                        "Appointment cancelled successfully. A confirmation email has been sent.", null));
            } catch (Exception emailEx) {
                System.err.println("Error sending cancellation email: " + emailEx.getMessage());
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
                        "Appointment cancelled, but failed to send confirmation email.", null));
            }

            loadAppointments(); // Refresh data and re-apply filters/pagination
            return "recipient-appointments?faces-redirect=true";

        } catch (Exception e) {
            System.err.println("Error cancelling appointment: " + e.getMessage());
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
        this.pageSize = pageSize;
        this.currentPage = 1; // Reset to first page if page size changes
        updateFilteredAppointments();
    }
}