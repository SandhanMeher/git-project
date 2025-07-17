package com.infinite.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration; // Corrected import: AnnotationConfiguration is deprecated.
import org.apache.log4j.Logger; // Import Log4j Logger

/**
 * The `SessionHelper` class provides a singleton instance of the Hibernate
 * `SessionFactory`. This factory is responsible for creating `Session` objects,
 * which are used to interact with the database. The `SessionFactory` is
 * initialized once during application startup.
 */
public class SessionHelper {

	private static final Logger logger = Logger.getLogger(SessionHelper.class);

	private static final SessionFactory sessionFactory;

	static {
		try {
			// Using Configuration to build SessionFactory.
			// AnnotationConfiguration is deprecated since Hibernate 3.x
			// In modern Hibernate (4+), Configuration directly supports annotations.
			sessionFactory = new Configuration().configure().buildSessionFactory();
			logger.info("Hibernate SessionFactory initialized successfully.");
		} catch (Throwable ex) {
			// Log the exception to a proper logging system
			logger.fatal("Initial SessionFactory creation failed.", ex);
			// Re-throw as ExceptionInInitializerError as this is a critical startup failure
			throw new ExceptionInInitializerError(ex);
		}
	}

	/**
	 * Returns the singleton instance of the Hibernate `SessionFactory`.
	 * This factory can then be used to open new Hibernate `Session` objects.
	 *
	 * @return The initialized `SessionFactory` instance.
	 */
	public static SessionFactory getSessionFactory() {
		return sessionFactory;
	}
}