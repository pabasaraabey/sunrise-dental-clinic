package lk.sdcms.model;

import java.util.Set;

public class Administrator extends User {

    public Administrator() {
        setRole(Role.ADMINISTRATOR);
    }

    @Override
    public Set<String> getPermissions() {
        return Set.of(
                "USER_MANAGE",
                "DENTIST_MANAGE",
                "TREATMENT_PRICE_MANAGE",
                "APPOINTMENT_VIEW",
                "REPORT_VIEW_ALL",
                "REPORT_REVENUE"
        );
    }
}
