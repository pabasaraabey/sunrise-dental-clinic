package lk.sdcms.pattern;

import lk.sdcms.model.Appointment;
import lk.sdcms.model.Bill;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Full price, no concession. */
public class StandardBilling implements BillingStrategy {

    /** Sri Lankan VAT at the prevailing rate. */
    public static final BigDecimal VAT_RATE = new BigDecimal("0.08");

    @Override
    public BigDecimal calculate(Appointment appointment,
                                BigDecimal consultationFee, Bill bill) {

        BigDecimal treatmentCost = appointment.getTreatment().getBaseCost();
        BigDecimal subtotal      = treatmentCost.add(consultationFee);
        BigDecimal vat           = subtotal.multiply(VAT_RATE)
                                           .setScale(2, RoundingMode.HALF_UP);

        bill.setConsultationFee(consultationFee);
        bill.setTreatmentCost(treatmentCost);
        bill.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        bill.setTaxAmount(vat);

        return subtotal.add(vat).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String description() {
        return "Standard rate";
    }
}
