package lk.sdcms.model;

import java.time.LocalDate;
import java.time.Period;

public class Patient {

    /** Age at which the clinic's concession applies. */
    public static final int SENIOR_CITIZEN_AGE = 65;

    private Long      patientId;
    private String    fullName;
    private String    address;
    private String    contactNo;
    private String    email;
    private LocalDate dateOfBirth;
    private Gender    gender;
    private String    medicalNotes;
    private LocalDate registeredDate;

    public Patient() {
    }

    /**
     * Age is derived, never stored. A stored age is correct only on the day it
     * is written; since senior-citizen billing depends on it, a stale value
     * would produce an incorrect bill. Deriving it also removes a functionally
     * dependent column, satisfying normalisation.
     */
    public int getAge() {
        if (dateOfBirth == null) {
            throw new IllegalStateException("dateOfBirth is required to derive age");
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public boolean isSeniorCitizen() {
        return getAge() >= SENIOR_CITIZEN_AGE;
    }

    public Long getPatientId()                  { return patientId; }
    public void setPatientId(Long id)           { this.patientId = id; }

    public String getFullName()                 { return fullName; }
    public void setFullName(String n)           { this.fullName = n; }

    public String getAddress()                  { return address; }
    public void setAddress(String a)            { this.address = a; }

    public String getContactNo()                { return contactNo; }
    public void setContactNo(String c)          { this.contactNo = c; }

    public String getEmail()                    { return email; }
    public void setEmail(String e)              { this.email = e; }

    public LocalDate getDateOfBirth()           { return dateOfBirth; }
    public void setDateOfBirth(LocalDate d)     { this.dateOfBirth = d; }

    public Gender getGender()                   { return gender; }
    public void setGender(Gender g)             { this.gender = g; }

    public String getMedicalNotes()             { return medicalNotes; }
    public void setMedicalNotes(String m)       { this.medicalNotes = m; }

    public LocalDate getRegisteredDate()        { return registeredDate; }
    public void setRegisteredDate(LocalDate d)  { this.registeredDate = d; }
}
