package com.infinite.dto;

import java.io.Serializable;
import java.sql.Date;
import java.util.List;
import java.util.Objects; // Added for Objects.hash and Objects.equals
import com.infinite.model.DoctorAvailability;

/**
 * `GroupedAvailability` is a Data Transfer Object (DTO) that encapsulates
 * doctor availability slots grouped by a specific date. It is designed to
 * facilitate the transfer of aggregated availability information, typically
 * from the data access layer to the presentation layer.
 */
public class GroupedAvailability implements Serializable {

    private static final long serialVersionUID = 1L; // Recommended for Serializable classes

    private Date date;
    private List<DoctorAvailability> slots;

    /**
     * Constructs a new `GroupedAvailability` instance.
     *
     * @param date  The `java.sql.Date` representing the date for which the slots are available.
     * @param slots A `List` of `DoctorAvailability` objects pertaining to the specified date.
     */
    public GroupedAvailability(Date date, List<DoctorAvailability> slots) {
        this.date = date;
        this.slots = slots;
    }

    /**
     * Returns the date for which the doctor availability slots are grouped.
     *
     * @return The `java.sql.Date` object.
     */
    public Date getDate() {
        return date;
    }

    /**
     * Returns the list of `DoctorAvailability` slots for the grouped date.
     *
     * @return A `List` of `DoctorAvailability` objects.
     */
    public List<DoctorAvailability> getSlots() {
        return slots;
    }

    /**
     * Setter for the date.
     *
     * @param date The date to set.
     */
    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * Setter for the list of slots.
     *
     * @param slots The list of DoctorAvailability slots to set.
     */
    public void setSlots(List<DoctorAvailability> slots) {
        this.slots = slots;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * The comparison is based on the `date` and the `slots` list.
     *
     * @param o The reference object with which to compare.
     * @return `true` if this object is the same as the obj argument; `false` otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupedAvailability that = (GroupedAvailability) o;
        return Objects.equals(date, that.date) &&
               Objects.equals(slots, that.slots);
    }

    /**
     * Returns a hash code value for the object. This method is supported for the benefit
     * of hash tables such as those provided by `HashMap`.
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(date, slots);
    }

    /**
     * Returns a string representation of the object.
     *
     * @return A string representation of the `GroupedAvailability` object.
     */
    @Override
    public String toString() {
        return "GroupedAvailability{" +
               "date=" + date +
               ", slots=" + slots +
               '}';
    }
}