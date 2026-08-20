package lk.sdcms.model;

import java.util.Set;

public class Receptionist extends User {

    private String counterNo;

    public Receptionist() {
        setRole(Role.RECEPTIONIST);
    }

    /**
     * Deliberately excludes TREATMENT_PRICE_MANAGE. A receptionist who could
     * both set prices and issue bills could under-charge and conceal it —
     * separation of duties.
     */
    @Override
    public Set<String> getPermissions() {
        return Set.of(
                "PATIENT_REGISTER",
                "APPOINTMENT_BOOK",
                "APPOINTMENT_VIEW",
                "APPOINTMENT_CANCEL",
                "BILL_GENERATE",
                "REPORT_VIEW_SCHEDULE"
        );
    }

    public String getCounterNo()            { return counterNo; }
    public void setCounterNo(String c)      { this.counterNo = c; }
}
