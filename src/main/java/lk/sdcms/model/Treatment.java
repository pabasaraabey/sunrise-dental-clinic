package lk.sdcms.model;

import java.math.BigDecimal;

public class Treatment {

    private Long       treatmentId;
    private String     name;
    private String     description;
    private BigDecimal baseCost;
    private int        durationMinutes = 30;
    private boolean    active = true;

    public Treatment() {
    }

    public Treatment(Long treatmentId, String name, BigDecimal baseCost) {
        this.treatmentId = treatmentId;
        this.name        = name;
        this.baseCost    = baseCost;
    }

    public Long getTreatmentId()                { return treatmentId; }
    public void setTreatmentId(Long id)         { this.treatmentId = id; }

    public String getName()                     { return name; }
    public void setName(String n)               { this.name = n; }

    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }

    /** Always BigDecimal — see Bill for why double is unacceptable here. */
    public BigDecimal getBaseCost()             { return baseCost; }
    public void setBaseCost(BigDecimal c)       { this.baseCost = c; }

    public int getDurationMinutes()             { return durationMinutes; }
    public void setDurationMinutes(int m)       { this.durationMinutes = m; }

    public boolean isActive()                   { return active; }
    public void setActive(boolean a)            { this.active = a; }
}
