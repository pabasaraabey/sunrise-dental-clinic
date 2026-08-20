package lk.sdcms.model;

import java.time.LocalTime;
import java.util.Set;

public class Dentist extends User {

    private Long      dentistId;
    private String    licenseNo;
    private String    specialization;
    private LocalTime availableFrom;
    private LocalTime availableTo;
    private String    consultationRoom;

    public Dentist() {
        setRole(Role.DENTIST);
    }

    /** Read-only access. A dentist has no operational need to issue bills. */
    @Override
    public Set<String> getPermissions() {
        return Set.of(
                "APPOINTMENT_VIEW_OWN",
                "PATIENT_HISTORY_VIEW"
        );
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

    public String getLicenseNo()                { return licenseNo; }
    public void setLicenseNo(String l)          { this.licenseNo = l; }

    public String getSpecialization()           { return specialization; }
    public void setSpecialization(String s)     { this.specialization = s; }

    public LocalTime getAvailableFrom()         { return availableFrom; }
    public void setAvailableFrom(LocalTime t)   { this.availableFrom = t; }

    public LocalTime getAvailableTo()           { return availableTo; }
    public void setAvailableTo(LocalTime t)     { this.availableTo = t; }

    public String getConsultationRoom()         { return consultationRoom; }
    public void setConsultationRoom(String r)   { this.consultationRoom = r; }
}
