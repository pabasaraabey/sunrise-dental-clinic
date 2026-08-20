package lk.sdcms.dto;

import lk.sdcms.model.Appointment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** What the client is told about an appointment. */
public class AppointmentDto {

    private String     appointmentNo;
    private String     patientName;
    private String     contactNo;
    private String     address;
    private int        patientAge;
    private String     dentistName;
    private String     specialization;
    private String     treatmentName;
    private BigDecimal treatmentCost;
    private LocalDate  appointmentDate;
    private LocalTime  appointmentTime;
    private String     status;
    private String     notes;
    private boolean    cancellable;

    public static AppointmentDto from(Appointment a) {
        AppointmentDto dto = new AppointmentDto();
        dto.appointmentNo   = a.getAppointmentNo();
        dto.patientName     = a.getPatient().getFullName();
        dto.contactNo       = a.getPatient().getContactNo();
        dto.address         = a.getPatient().getAddress();
        dto.patientAge      = a.getPatient().getAge();
        dto.dentistName     = a.getDentist().getFullName();
        dto.specialization  = a.getDentist().getSpecialization();
        dto.treatmentName   = a.getTreatment().getName();
        dto.treatmentCost   = a.getTreatment().getBaseCost();
        dto.appointmentDate = a.getAppointmentDate();
        dto.appointmentTime = a.getAppointmentTime();
        dto.status          = a.getStatus().name();
        dto.notes           = a.getNotes();
        dto.cancellable     = a.isCancellable();
        return dto;
    }

    public String     getAppointmentNo()   { return appointmentNo; }
    public String     getPatientName()     { return patientName; }
    public String     getContactNo()       { return contactNo; }
    public String     getAddress()         { return address; }
    public int        getPatientAge()      { return patientAge; }
    public String     getDentistName()     { return dentistName; }
    public String     getSpecialization()  { return specialization; }
    public String     getTreatmentName()   { return treatmentName; }
    public BigDecimal getTreatmentCost()   { return treatmentCost; }
    public LocalDate  getAppointmentDate() { return appointmentDate; }
    public LocalTime  getAppointmentTime() { return appointmentTime; }
    public String     getStatus()          { return status; }
    public String     getNotes()           { return notes; }
    public boolean    isCancellable()      { return cancellable; }
}
