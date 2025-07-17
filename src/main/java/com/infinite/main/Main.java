package com.infinite.main;

import java.util.List;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.infinite.dao.AppointmentDaoImpl;
import com.infinite.model.Appointment;
import com.infinite.model.AppointmentStatus;

public class Main {
	public static void main(String[] args) {
		String hId = "H1003"; // Replace with actual recipient ID
		AppointmentDaoImpl dao = new AppointmentDaoImpl();

		try {
			List<Appointment> upcomingAppointments = dao.getUpcomingAppointmentsByRecipient(hId);

			System.out.println("=== FUTURE APPOINTMENTS FOR H1003 ===");
			int count = 0;
			for (Appointment appt : upcomingAppointments) {
				if (appt.getStart() != null && appt.getStart().after(Timestamp.valueOf(LocalDateTime.now()))) {
					if (appt.getStatus() == AppointmentStatus.PENDING || appt.getStatus() == AppointmentStatus.BOOKED
							|| appt.getStatus() == AppointmentStatus.CANCELLED) {
						System.out.println("ID: " + appt.getAppointment_id());
						System.out.println("Status: " + appt.getStatus());
						System.out.println("Start: " + appt.getStart());
						System.out.println("Doctor: " + appt.getDoctor().getDoctor_name());
						System.out.println("Notes: " + appt.getNotes());
						System.out.println("-------------------------------------");
						count++;
					}
				}
			}

			if (count == 0) {
				System.out.println("❌ No future appointments found for H1003.");
			} else {
				System.out.println("✅ Total Future Appointments: " + count);
			}
		} catch (Exception e) {
			System.err.println("Error fetching appointments: " + e.getMessage());
		}
	}
}
