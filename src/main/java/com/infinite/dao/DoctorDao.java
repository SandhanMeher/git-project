package com.infinite.dao;

import java.util.List;

import com.infinite.model.Doctors;

/**
 * The `DoctorDao` interface defines the contract for data access operations
 * related to `Doctors` entities. It specifies methods for retrieving doctor
 * information from the persistence layer.
 */
public interface DoctorDao {

	/**
	 * Retrieves a list of all doctors who have an 'APPROVED' status.
	 *
	 * @return A {@code List} of {@code Doctors} objects representing all approved doctors.
	 */
	public List<Doctors> getAllApprovedDoctor();

	/**
	 * Retrieves a list of all doctors associated with a specific healthcare provider
	 * who also have an 'APPROVED' status.
	 *
	 * @param providerId The unique identifier of the healthcare provider.
	 * @return A {@code List} of {@code Doctors} objects representing approved doctors
	 * under the given provider.
	 */
	public List<Doctors> getApprovedDoctorsByProviderId(String providerId);

	/**
	 * Searches for and retrieves a single doctor by their unique doctor ID.
	 *
	 * @param doctorId The unique identifier of the doctor to search for.
	 * @return A {@code Doctors} object if a doctor with the specified ID is found,
	 * otherwise {@code null}.
	 */
	public Doctors searchADoctorById(String doctorId);
}