package lk.sdcms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * A booked visit.
 *
 * <p>Constructed through {@link Builder} rather than a positional constructor.
 * This type carries eight fields, several of them the same type — a
 * {@code Appointment(patient, dentist, treatment, date, time, ...)} call would
 * compile perfectly with two arguments transposed and fail only at runtime, or
 * worse, silently produce a plausible but wrong record. Naming each value at
 * the point of assignment makes that class of error impossible.
 */
public class Appointment {

    private static final DateTimeFormatter NO_DATE_PART =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Cancellations are refused inside this window before the appointment. */
    public static final int CANCELLATION_CUTOFF_HOURS = 2;

    private String            appointmentNo;
    private Patient           patient;
    private Dentist           dentist;
    private Treatment         treatment;
    private LocalDate         appointmentDate;
    private LocalTime         appointmentTime;
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;
    private String            notes;
    private Long              createdBy;
    private LocalDateTime     createdAt;

    private Appointment() {
    }

    /**
     * Builds an identifier of the form {@code APT-20260821-0042}.
     *
     * <p>The scenario lists the appointment number among the details staff
     * collect, which reads as manual entry. Generating it instead guarantees
     * uniqueness, embeds the date for easier retrieval, and removes a
     * data-entry step during busy periods — manual entry of a value required
     * to be unique is a well-known source of duplicate keys.
     */
    public static String buildAppointmentNo(LocalDate date, int sequence) {
        return "APT-" + date.format(NO_DATE_PART)
                + "-" + String.format("%04d", sequence);
    }

    /** Start of the appointment as a single instant, for time comparisons. */
    public LocalDateTime getStartsAt() {
        return LocalDateTime.of(appointmentDate, appointmentTime);
    }

    /**
     * Whether cancellation is still permitted. Refusing late cancellations
     * gives the clinic a chance to re-sell the freed slot.
     */
    public boolean isCancellable() {
        if (status != AppointmentStatus.SCHEDULED) {
            return false;
        }
        return LocalDateTime.now()
                .plusHours(CANCELLATION_CUTOFF_HOURS)
                .isBefore(getStartsAt());
    }

    public String            getAppointmentNo()    { return appointmentNo; }
    public Patient           getPatient()          { return patient; }
    public Dentist           getDentist()          { return dentist; }
    public Treatment         getTreatment()        { return treatment; }
    public LocalDate         getAppointmentDate()  { return appointmentDate; }
    public LocalTime         getAppointmentTime()  { return appointmentTime; }
    public AppointmentStatus getStatus()           { return status; }
    public String            getNotes()            { return notes; }
    public Long              getCreatedBy()        { return createdBy; }
    public LocalDateTime     getCreatedAt()        { return createdAt; }

    public void setAppointmentNo(String no)        { this.appointmentNo = no; }
    public void setStatus(AppointmentStatus s)     { this.status = s; }
    public void setCreatedAt(LocalDateTime t)      { this.createdAt = t; }

    // -----------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Appointment target = new Appointment();

        public Builder appointmentNo(String no) {
            target.appointmentNo = no;
            return this;
        }

        public Builder patient(Patient patient) {
            target.patient = patient;
            return this;
        }

        public Builder dentist(Dentist dentist) {
            target.dentist = dentist;
            return this;
        }

        public Builder treatment(Treatment treatment) {
            target.treatment = treatment;
            return this;
        }

        public Builder date(LocalDate date) {
            target.appointmentDate = date;
            return this;
        }

        public Builder time(LocalTime time) {
            target.appointmentTime = time;
            return this;
        }

        public Builder status(AppointmentStatus status) {
            target.status = status;
            return this;
        }

        public Builder notes(String notes) {
            target.notes = notes;
            return this;
        }

        public Builder createdBy(Long userId) {
            target.createdBy = userId;
            return this;
        }

        public Builder createdAt(LocalDateTime at) {
            target.createdAt = at;
            return this;
        }

        /**
         * Checks invariants before releasing the object, so an Appointment can
         * never exist in a half-populated state. Without this, a missing
         * dentist would surface much later as a NullPointerException far from
         * the code that actually caused it.
         */
        public Appointment build() {
            Objects.requireNonNull(target.patient,         "patient is required");
            Objects.requireNonNull(target.dentist,         "dentist is required");
            Objects.requireNonNull(target.treatment,       "treatment is required");
            Objects.requireNonNull(target.appointmentDate, "date is required");
            Objects.requireNonNull(target.appointmentTime, "time is required");
            Objects.requireNonNull(target.createdBy,       "createdBy is required");

            if (target.status == null) {
                target.status = AppointmentStatus.SCHEDULED;
            }
            return target;
        }
    }
}
