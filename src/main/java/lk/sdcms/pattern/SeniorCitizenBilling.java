package lk.sdcms.pattern;

import lk.sdcms.model.Appointment;
import lk.sdcms.model.Bill;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ten percent concession for patients aged 65 and above.
 *
 * <p><b>Order of operations matters.</b> The discount is applied first, then
 * VAT is charged on the discounted subtotal. Taxing before discounting yields a
 * different — and higher — total, and would mean the clinic collecting VAT on
 * money the patient never paid.
 */
public class SeniorCitizenBilling implements BillingStrategy {

    public static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10");
    public static final BigDecimal VAT_RATE      = new BigDecimal("0.08");

    @Override
    public BigDecimal calculate(Appointment appointment,
                                BigDecimal consultationFee, Bill bill) {

        BigDecimal treatmentCost = appointment.getTreatment().getBaseCost();
        BigDecimal subtotal      = treatmentCost.add(consultationFee);

        BigDecimal discount = subtotal.multiply(DISCOUNT_RATE)
                                      .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxable = subtotal.subtract(discount);

        BigDecimal vat = taxable.multiply(VAT_RATE)
                                .setScale(2, RoundingMode.HALF_UP);

        bill.setConsultationFee(consultationFee);
        bill.setTreatmentCost(treatmentCost);
        bill.setDiscountAmount(discount);
        bill.setTaxAmount(vat);

        return taxable.add(vat).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String description() {
        return "Senior citizen concession (10%)";
    }
}
