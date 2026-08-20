package lk.sdcms.pattern;

import lk.sdcms.model.Appointment;
import lk.sdcms.model.Bill;

import java.math.BigDecimal;

/**
 * Strategy pattern — one pricing rule.
 *
 * <p>Billing rules change. The clinic currently applies a standard rate and a
 * senior-citizen concession, and expects to add insurance settlement. Encoding
 * those as branches inside a single method would mean editing and re-testing
 * that method every time policy shifts, and the branches would accumulate.
 *
 * <p>With this interface a new rule is a new class. BillingService is never
 * touched — the Open/Closed Principle in practice rather than in theory.
 */
public interface BillingStrategy {

    /** Populates the amount fields on the bill and returns the total. */
    BigDecimal calculate(Appointment appointment, BigDecimal consultationFee, Bill bill);

    /** Shown on the receipt so the patient can see which rule was applied. */
    String description();
}
