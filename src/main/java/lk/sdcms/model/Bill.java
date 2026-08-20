package lk.sdcms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A financial record of what was charged for one appointment.
 *
 * <p><b>Every monetary field is BigDecimal, never double.</b> Binary floating
 * point cannot represent decimal fractions such as 0.1 exactly, so repeated
 * arithmetic accumulates drift. Since the scenario names billing errors among
 * the clinic's existing problems, using a type incapable of representing
 * currency accurately would reintroduce the very fault the system exists to
 * remove.
 *
 * <p>Amounts are stored rather than recalculated on demand. This is a
 * deliberate departure from strict normalisation: a bill records what the
 * patient actually paid at a point in time. If the administrator later revises
 * a treatment price, recalculating would retrospectively falsify the accounts.
 */
public class Bill {

    private String        billId;
    private String        appointmentNo;
    private BigDecimal    consultationFee;
    private BigDecimal    treatmentCost;
    private BigDecimal    discountAmount = BigDecimal.ZERO;
    private BigDecimal    taxAmount;
    private BigDecimal    totalAmount;
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    private PaymentMethod paymentMethod;
    private Long          issuedBy;
    private LocalDateTime issuedDate;

    public Bill() {
    }

    public String getBillId()                       { return billId; }
    public void setBillId(String id)                { this.billId = id; }

    public String getAppointmentNo()                { return appointmentNo; }
    public void setAppointmentNo(String no)         { this.appointmentNo = no; }

    public BigDecimal getConsultationFee()          { return consultationFee; }
    public void setConsultationFee(BigDecimal f)    { this.consultationFee = f; }

    public BigDecimal getTreatmentCost()            { return treatmentCost; }
    public void setTreatmentCost(BigDecimal c)      { this.treatmentCost = c; }

    public BigDecimal getDiscountAmount()           { return discountAmount; }
    public void setDiscountAmount(BigDecimal d)     { this.discountAmount = d; }

    public BigDecimal getTaxAmount()                { return taxAmount; }
    public void setTaxAmount(BigDecimal t)          { this.taxAmount = t; }

    public BigDecimal getTotalAmount()              { return totalAmount; }
    public void setTotalAmount(BigDecimal t)        { this.totalAmount = t; }

    public PaymentStatus getPaymentStatus()         { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus s)   { this.paymentStatus = s; }

    public PaymentMethod getPaymentMethod()         { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod m)   { this.paymentMethod = m; }

    public Long getIssuedBy()                       { return issuedBy; }
    public void setIssuedBy(Long id)                { this.issuedBy = id; }

    public LocalDateTime getIssuedDate()            { return issuedDate; }
    public void setIssuedDate(LocalDateTime d)      { this.issuedDate = d; }
}
