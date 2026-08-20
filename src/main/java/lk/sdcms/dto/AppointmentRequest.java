package lk.sdcms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Shape of the JSON body posted to /api/appointments. */
public class AppointmentRequest {

    // Patient details — an existing patient is matched on contactNo,
    // otherwise a new record is created from these fields.
    private String    patientName;
    private String    address;
    private String    contactNo;
    private String    email;
    private LocalDate dateOfBirth;
    private String    gender;

    private Long      dentistId;
    private Long      treatmentId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private String    notes;

    public String getPatientName()              { return patientName; }
    public void setPatientName(String v)        { this.patientName = v; }

    public String getAddress()                  { return address; }
    public void setAddress(String v)            { this.address = v; }

    public String getContactNo()                { return contactNo; }
    public void setContactNo(String v)          { this.contactNo = v; }

    public String getEmail()                    { return email; }
    public void setEmail(String v)              { this.email = v; }

    public LocalDate getDateOfBirth()           { return dateOfBirth; }
    public void setDateOfBirth(LocalDate v)     { this.dateOfBirth = v; }

    public String getGender()                   { return gender; }
    public void setGender(String v)             { this.gender = v; }

    public Long getDentistId()                  { return dentistId; }
    public void setDentistId(Long v)            { this.dentistId = v; }

    public Long getTreatmentId()                { return treatmentId; }
    public void setTreatmentId(Long v)          { this.treatmentId = v; }

    public LocalDate getAppointmentDate()       { return appointmentDate; }
    public void setAppointmentDate(LocalDate v) { this.appointmentDate = v; }

    public LocalTime getAppointmentTime()       { return appointmentTime; }
    public void setAppointmentTime(LocalTime v) { this.appointmentTime = v; }

    public String getNotes()                    { return notes; }
    public void setNotes(String v)              { this.notes = v; }
}
