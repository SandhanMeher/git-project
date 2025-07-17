package com.infinite.util;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger; // Import Log4j Logger

import com.infinite.model.AppointmentSlip;

/**
 * The `MailSend` class provides utility methods for sending emails,
 * specifically tailored for appointment-related notifications.
 * It uses JavaMail API for sending HTML-formatted emails.
 */
public class MailSend {

	private static final Logger logger = Logger.getLogger(MailSend.class);

	// Consider externalizing these credentials for security in a real application
	private static final String FROM_EMAIL = "infinitehealthsure@gmail.com";
	private static final String APP_PASSWORD = "xrascqydsfthxttk"; // This should be an app-specific password, not the main account password
	private static final String SMTP_HOST = "smtp.gmail.com";
	private static final String SMTP_PORT = "465";

	/**
	 * Sends an email with the specified recipient, subject, and HTML content.
	 * This method configures the SMTP properties for Gmail and authenticates
	 * using the provided sender email and app password.
	 *
	 * @param toEmail     The email address of the recipient.
	 * @param subject     The subject line of the email.
	 * @param htmlContent The HTML content of the email body.
	 * @return A String indicating "Mail Sent Successfully..." on success,
	 * or an error message on failure.
	 */
	public static String sendInfo(String toEmail, String subject, String htmlContent) {
		logger.info("Attempting to send email to: " + toEmail + " with subject: " + subject);

		Properties properties = new Properties();
		properties.put("mail.smtp.host", SMTP_HOST);
		properties.put("mail.smtp.port", SMTP_PORT);
		properties.put("mail.smtp.ssl.enable", "true"); // Use SSL/TLS for secure connection
		properties.put("mail.smtp.auth", "true"); // Enable authentication

		// Create a session with authentication
		Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				// Provide the sender's email and the app password
				return new PasswordAuthentication(FROM_EMAIL, APP_PASSWORD);
			}
		});

		session.setDebug(false); // Set to true for debugging mail session issues, false for production
		logger.debug("Mail session created. Debugging is set to: " + session.getDebug());

		try {
			// Create a new MimeMessage
			MimeMessage message = new MimeMessage(session);

			// Set the sender address
			message.setFrom(new InternetAddress(FROM_EMAIL));

			// Set the recipient address
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));

			// Set the email subject
			message.setSubject(subject);

			// Set the email content as HTML
			message.setContent(htmlContent, "text/html");

			// Send the message
			Transport.send(message);
			logger.info("Email sent successfully to " + toEmail);
			return "Mail Sent Successfully...";
		} catch (MessagingException mex) {
			logger.error("Error sending email to " + toEmail + ": " + mex.getMessage(), mex);
			return "Error: " + mex.getMessage();
		} catch (Exception e) {
			logger.error("An unexpected error occurred while sending email to " + toEmail + ": " + e.getMessage(), e);
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Generates the HTML content for an appointment request confirmation email.
	 * This content includes details like appointment ID, doctor, date, time slot,
	 * and provider contact information.
	 *
	 * @param apSli The `AppointmentSlip` object containing all necessary appointment details.
	 * @return A String containing the HTML content for the appointment request email.
	 */
	public static String appointmentRequest(AppointmentSlip apSli) {
		logger.info("Generating appointment request HTML content for appointment ID: " + apSli.getAppointmentId());
		String htmlContent = "<html><body style='font-family:Arial, sans-serif;'>"
				+ "<h2 style='color:#2E86C1;'>Your appointment request has been received</h2>"
				+ "<p>Dear " + apSli.getPatientName() + ",</p>"
				+ "<p>Thank you for booking your appointment with <strong>" + apSli.getProviderName() + "</strong>.</p>"
				+ "<table style='border-collapse: collapse; width: 100%; margin-top: 10px;'>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Appointment ID</td><td style='padding: 8px; border: 1px solid #ddd;'>"
				+ apSli.getAppointmentId() + "</td></tr>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Doctor</td><td style='padding: 8px; border: 1px solid #ddd;'>"
				+ apSli.getDoctorName() + " (" + apSli.getDoctorSpecialization() + ")</td></tr>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Date</td><td style='padding: 8px; border: 1px solid #ddd;'>"
				+ apSli.getDate() + "</td></tr>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Time</td><td style='padding: 8px; border: 1px solid #ddd;'>Slot "
				+ apSli.getSlotNo() + " - " + apSli.getTiming() + "</td></tr>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Provider Contact</td><td style='padding: 8px; border: 1px solid #ddd;'>"
				+ apSli.getProviderEmail() + " | " + apSli.getProviderNumber() + "</td></tr>"
				+ "</table>"
				+ "<p style='margin-top: 20px;'>You will receive a confirmation once the provider reviews and approves your request."
				+ "<br><br>Thank you for choosing Infinite HealthSure.</p>"
				+ "<p style='margin-top: 20px;'>Please arrive 15 minutes early and bring any necessary documents.</p>"
				+ "<hr></body></html>";
		return htmlContent;
	}

	/**
	 * Generates the HTML content for an appointment cancellation confirmation email.
	 * This content informs the patient about the cancellation and provides
	 * relevant appointment details.
	 *
	 * @param apSli The `AppointmentSlip` object containing all necessary appointment details.
	 * @return A String containing the HTML content for the appointment cancellation email.
	 */
	public static String appointmentCancellation(AppointmentSlip apSli) {
		logger.info("Generating appointment cancellation HTML content for appointment ID: " + apSli.getAppointmentId());
		String htmlContent = "<html><body style='font-family:Arial, sans-serif;'>"
				+ "<h2 style='color:#C0392B;'>Your appointment has been cancelled</h2>"
				+ "<p>Dear " + apSli.getPatientName() + ",</p>"
				+ "<p>Your appointment with <strong>" + apSli.getDoctorName() + "</strong> at <strong>"
				+ apSli.getProviderName() + "</strong> has been successfully cancelled.</p>"
				+ "<table style='border-collapse: collapse; width: 100%; margin-top: 10px;'>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Appointment ID</td>"
				+ "<td style='padding: 8px; border: 1px solid #ddd;'>" + apSli.getAppointmentId() + "</td></tr>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Date</td>"
				+ "<td style='padding: 8px; border: 1px solid #ddd;'>" + apSli.getDate() + "</td></tr>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Time</td>"
				+ "<td style='padding: 8px; border: 1px solid #ddd;'>Slot " + apSli.getSlotNo() + " - "
				+ apSli.getTiming() + "</td></tr>"
				+ "<tr><td style='padding: 8px; border: 1px solid #ddd;'>Provider Contact</td>"
				+ "<td style='padding: 8px; border: 1px solid #ddd;'>" + apSli.getProviderEmail() + " | "
				+ apSli.getProviderNumber() + "</td></tr>"
				+ "</table>"
				+ "<p style='margin-top: 20px;'>If this was a mistake or you want to reschedule, please contact us or book a new appointment.</p>"
				+ "<p>Thank thank you,<br>Infinite HealthSure Team</p>" // Corrected "Thank thank you" to "Thank you"
				+ "<hr></body></html>";
		return htmlContent;
	}
}

