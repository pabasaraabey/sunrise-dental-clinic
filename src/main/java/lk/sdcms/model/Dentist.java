package lk.sdcms.model;

import java.time.LocalTime;

/**
 * A dentist practising at the clinic.
 *
 * <p>A plain record, not a system user. Reception maintains these so names and
 * specialisations appear on appointments and receipts; dentists have no login.
 */
public class Dentist {

    private Long      dentistId;
    private String    fullName;
    private String    licenseNo;
    private String    specialization;
    private String    contactNo;
    private LocalTime availableFrom = LocalTime.of(8, 0);
    private LocalTime availableTo   = LocalTime.of(20, 0);
    private String    consultationRoom;
    private boolean   active = true;

    public Dentist() {
    }

    /**
     * Whether the requested time falls inside this dentist's working hours.
     * Inclusive of the start, exclusive of the end — a 20:00 finish means the
     * last bookable slot starts before 20:00, not at it.
     */
    public boolean isWithinWorkingHours(LocalTime time) {
        if (availableFrom == null || availableTo == null) {
            return false;
        }
        return !time.isBefore(availableFrom) && time.isBefore(availableTo);
    }

    public Long getDentistId()                  { return dentistId; }
    public void setDentistId(Long id)           { this.dentistId = id; }

    public String getFullName()                 { return fullName; }
    public void setFullName(String n)           { this.fullName = n; }

    public String getLicenseNo()                { return licenseNo; }
    public void setLicenseNo(String l)          { this.licenseNo = l; }

    public String getSpecialization()           { return specialization; }
    public void setSpecialization(String s)     { this.specialization = s; }

    public String getContactNo()                { return contactNo; }
    public void setContactNo(String c)          { this.contactNo = c; }

    public LocalTime getAvailableFrom()         { return availableFrom; }
    public void setAvailableFrom(LocalTime t)   { this.availableFrom = t; }

    public LocalTime getAvailableTo()           { return availableTo; }
    public void setAvailableTo(LocalTime t)     { this.availableTo = t; }

    public String getConsultationRoom()         { return consultationRoom; }
    public void setConsultationRoom(String r)   { this.consultationRoom = r; }

    public boolean isActive()                   { return active; }
    public void setActive(boolean a)            { this.active = a; }
}
