package lk.sdcms.model;

import java.util.Set;

/**
 * The clinic's main operator.
 *
 * <p>Carries every operational duty. This deliberately forgoes the separation
 * of duties between price maintenance and billing that a larger organisation
 * would enforce; with three staff on site that separation is impractical, and
 * audit_log serves as the compensating control instead.
 */
public class Receptionist extends User {

    private String counterNo;

    public Receptionist() {
        setRole(Role.RECEPTIONIST);
    }

    @Override
    public Set<String> getPermissions() {
        return Set.of(
                "PATIENT_MANAGE",
                "APPOINTMENT_MANAGE",
                "BILL_MANAGE",
                "DENTIST_MANAGE",
                "TREATMENT_MANAGE",
                "REPORT_VIEW"
        );
    }

    public String getCounterNo()            { return counterNo; }
    public void setCounterNo(String c)      { this.counterNo = c; }
}
