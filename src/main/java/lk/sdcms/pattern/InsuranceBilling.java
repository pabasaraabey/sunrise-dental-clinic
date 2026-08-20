package lk.sdcms.pattern;

import lk.sdcms.model.Appointment;
import lk.sdcms.model.Bill;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Settlement where an insurer covers part of the cost.
 *
 * <p>Not yet reachable from the user interface — the clinic has no insurer
 * agreements in place. It exists to demonstrate the point of the Strategy
 * pattern: adding this rule required writing one class and changing nothing
 * else. When an agreement is signed, only the selection logic in BillingService
 * needs a line added.
 */
public class InsuranceBilling implements BillingStrategy {

    public static final BigDecimal VAT_RATE = new BigDecimal("0.08");

    private final BigDecimal coverageRate;

    /** @param coverageRate proportion met by the insurer, e.g. 0.60 for 60% */
    public InsuranceBilling(BigDecimal coverageRate) {
        if (coverageRate.compareTo(BigDecimal.ZERO) < 0
                || coverageRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Coverage must be between 0 and 1");
        }
        this.coverageRate = coverageRate;
    }

    @Override
    public BigDecimal calculate(Appointment appointment,
                                BigDecimal consultationFee, Bill bill) {

        BigDecimal treatmentCost = appointment.getTreatment().getBaseCost();
        BigDecimal subtotal      = treatmentCost.add(consultationFee);

        BigDecimal covered = subtotal.multiply(coverageRate)
                                     .setScale(2, RoundingMode.HALF_UP);

        BigDecimal payable = subtotal.subtract(covered);

        BigDecimal vat = payable.multiply(VAT_RATE)
                                .setScale(2, RoundingMode.HALF_UP);

        bill.setConsultationFee(consultationFee);
        bill.setTreatmentCost(treatmentCost);
        bill.setDiscountAmount(covered);
        bill.setTaxAmount(vat);

        return payable.add(vat).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String description() {
        return "Insurance settlement ("
                + coverageRate.multiply(new BigDecimal("100")).intValue() + "% covered)";
    }
}
