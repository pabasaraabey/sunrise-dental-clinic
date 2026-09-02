package lk.sdcms.model;

import java.util.Set;

/** Everything a receptionist may do, plus staff account management. */
public class Administrator extends User {

    public Administrator() {
        setRole(Role.ADMINISTRATOR);
    }

    @Override
    public Set<String> getPermissions() {
        return Set.of(
                "PATIENT_MANAGE",
                "APPOINTMENT_MANAGE",
                "BILL_MANAGE",
                "DENTIST_MANAGE",
                "TREATMENT_MANAGE",
                "REPORT_VIEW",
                "USER_MANAGE"
        );
    }
}
