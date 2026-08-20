package lk.sdcms.dto;

import lk.sdcms.model.Bill;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BillDto {

    private String        billId;
    private String        appointmentNo;
    private BigDecimal    consultationFee;
    private BigDecimal    treatmentCost;
    private BigDecimal    discountAmount;
    private BigDecimal    taxAmount;
    private BigDecimal    totalAmount;
    private String        paymentStatus;
    private String        paymentMethod;
    private String        appliedRule;
    private LocalDateTime issuedDate;

    public static BillDto from(Bill bill, String appliedRule) {
        BillDto dto = new BillDto();
        dto.billId          = bill.getBillId();
        dto.appointmentNo   = bill.getAppointmentNo();
        dto.consultationFee = bill.getConsultationFee();
        dto.treatmentCost   = bill.getTreatmentCost();
        dto.discountAmount  = bill.getDiscountAmount();
        dto.taxAmount       = bill.getTaxAmount();
        dto.totalAmount     = bill.getTotalAmount();
        dto.paymentStatus   = bill.getPaymentStatus().name();
        dto.paymentMethod   = bill.getPaymentMethod() == null
                ? null : bill.getPaymentMethod().name();
        dto.appliedRule     = appliedRule;
        dto.issuedDate      = bill.getIssuedDate();
        return dto;
    }

    public String        getBillId()          { return billId; }
    public String        getAppointmentNo()   { return appointmentNo; }
    public BigDecimal    getConsultationFee() { return consultationFee; }
    public BigDecimal    getTreatmentCost()   { return treatmentCost; }
    public BigDecimal    getDiscountAmount()  { return discountAmount; }
    public BigDecimal    getTaxAmount()       { return taxAmount; }
    public BigDecimal    getTotalAmount()     { return totalAmount; }
    public String        getPaymentStatus()   { return paymentStatus; }
    public String        getPaymentMethod()   { return paymentMethod; }
    public String        getAppliedRule()     { return appliedRule; }
    public LocalDateTime getIssuedDate()      { return issuedDate; }
}
