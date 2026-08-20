package lk.sdcms.service;

import lk.sdcms.model.*;
import lk.sdcms.pattern.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pricing arithmetic, tested without any database.
 *
 * <p>These are the calculations the clinic's money depends on, so the
 * assertions compare exact BigDecimal values rather than approximations —
 * comparing doubles with a tolerance would defeat the point of using
 * BigDecimal in the first place.
 */
class BillingStrategyTest {

    private static final BigDecimal CONSULTATION = new BigDecimal("1500.00");

    private Treatment treatment;

    @BeforeEach
    void setUp() {
        // 6500.00 chosen so intermediate values are not accidentally round.
        treatment = new Treatment(1L, "Composite Filling", new BigDecimal("6500.00"));
    }

    private Appointment appointmentForPatientAged(int age) {
        Patient patient = new Patient();
        patient.setPatientId(1L);
        patient.setFullName("Test Patient");
        patient.setContactNo("0771234567");
        patient.setDateOfBirth(LocalDate.now().minusYears(age));

        Dentist dentist = new Dentist();
        dentist.setDentistId(1L);
        dentist.setFullName("Dr. Test");

        return Appointment.builder()
                .appointmentNo("APT-20260821-0001")
                .patient(patient).dentist(dentist).treatment(treatment)
                .date(LocalDate.now().plusDays(1))
                .time(LocalTime.of(10, 0))
                .createdBy(1L)
                .build();
    }

    // ---------- standard rate ----------

    @Test
    @DisplayName("Standard billing: subtotal plus 8% VAT")
    void standardBillingArithmetic() {
        Bill bill = new Bill();
        BigDecimal total = new StandardBilling()
                .calculate(appointmentForPatientAged(40), CONSULTATION, bill);

        // 6500 + 1500 = 8000; VAT 8% = 640; total 8640
        assertEquals(new BigDecimal("640.00"),  bill.getTaxAmount());
        assertEquals(new BigDecimal("8640.00"), total);
        assertEquals(0, bill.getDiscountAmount().compareTo(BigDecimal.ZERO));
    }

    // ---------- senior concession ----------

    @Test
    @DisplayName("Senior billing: discount applied before VAT")
    void seniorBillingArithmetic() {
        Bill bill = new Bill();
        BigDecimal total = new SeniorCitizenBilling()
                .calculate(appointmentForPatientAged(70), CONSULTATION, bill);

        // 8000 - 10% = 7200 taxable; VAT 8% of 7200 = 576; total 7776
        assertEquals(new BigDecimal("800.00"),  bill.getDiscountAmount());
        assertEquals(new BigDecimal("576.00"),  bill.getTaxAmount());
        assertEquals(new BigDecimal("7776.00"), total);
    }

    /**
     * Guards the ordering explicitly. Taxing before discounting would give
     * 8000 + 640 = 8640, less 10% = 7776 — coincidentally the same here, so a
     * naive test would not catch the error. This asserts on the intermediate
     * tax figure, which differs: 576 versus 640.
     */
    @Test
    @DisplayName("VAT is charged on the discounted amount, not the gross")
    void vatFollowsDiscount() {
        Bill bill = new Bill();
        new SeniorCitizenBilling()
                .calculate(appointmentForPatientAged(70), CONSULTATION, bill);

        assertEquals(new BigDecimal("576.00"), bill.getTaxAmount(),
                "VAT must be 8% of 7200, not of 8000");
        assertNotEquals(new BigDecimal("640.00"), bill.getTaxAmount());
    }

    // ---------- the age boundary ----------

    @Test
    @DisplayName("A patient aged exactly 65 receives the concession")
    void exactlySixtyFiveQualifies() {
        BillingService service = new BillingService(null, null);
        BillingStrategy strategy =
                service.selectStrategy(appointmentForPatientAged(65));

        assertInstanceOf(SeniorCitizenBilling.class, strategy);
    }

    @Test
    @DisplayName("A patient aged 64 does not receive the concession")
    void sixtyFourDoesNotQualify() {
        BillingService service = new BillingService(null, null);
        BillingStrategy strategy =
                service.selectStrategy(appointmentForPatientAged(64));

        assertInstanceOf(StandardBilling.class, strategy);
    }

    // ---------- rounding and determinism ----------

    @Test
    @DisplayName("Totals are always scaled to two decimal places")
    void totalsAreScaledToTwoPlaces() {
        // 3333.33 produces a repeating value under the 10% discount, which is
        // where an unspecified RoundingMode would throw ArithmeticException.
        Treatment awkward = new Treatment(2L, "Odd price", new BigDecimal("3333.33"));

        Patient patient = new Patient();
        patient.setPatientId(1L);
        patient.setDateOfBirth(LocalDate.now().minusYears(70));

        Dentist dentist = new Dentist();
        dentist.setDentistId(1L);

        Appointment appointment = Appointment.builder()
                .appointmentNo("APT-20260821-0002")
                .patient(patient).dentist(dentist).treatment(awkward)
                .date(LocalDate.now().plusDays(1)).time(LocalTime.of(10, 0))
                .createdBy(1L).build();

        Bill bill = new Bill();
        BigDecimal total = new SeniorCitizenBilling()
                .calculate(appointment, CONSULTATION, bill);

        assertEquals(2, total.scale());
        assertEquals(2, bill.getTaxAmount().scale());
        assertEquals(2, bill.getDiscountAmount().scale());
    }

    @Test
    @DisplayName("Identical inputs always produce an identical total")
    void calculationIsDeterministic() {
        Appointment appointment = appointmentForPatientAged(70);

        BigDecimal first  = new SeniorCitizenBilling()
                .calculate(appointment, CONSULTATION, new Bill());
        BigDecimal second = new SeniorCitizenBilling()
                .calculate(appointment, CONSULTATION, new Bill());

        assertEquals(first, second);
    }

    /**
     * The reason every monetary value in this system is BigDecimal.
     *
     * <p>Demonstrates the failure directly rather than asserting it in the
     * abstract: the same sum in double arithmetic does not equal its exact
     * decimal value, and that error compounds across a day of billing.
     */
    @Test
    @DisplayName("Double arithmetic would introduce error where BigDecimal does not")
    void floatingPointWouldDrift() {
        double doubleSum = 0.0;
        for (int i = 0; i < 10; i++) {
            doubleSum += 0.1;
        }
        assertNotEquals(1.0, doubleSum, "double arithmetic drifts");

        BigDecimal exact = BigDecimal.ZERO;
        for (int i = 0; i < 10; i++) {
            exact = exact.add(new BigDecimal("0.1"));
        }
        assertEquals(0, exact.compareTo(BigDecimal.ONE), "BigDecimal is exact");
    }

    // ---------- insurance ----------

    @Test
    @DisplayName("Insurance billing charges only the uncovered portion")
    void insuranceCoversItsShare() {
        Bill bill = new Bill();
        BigDecimal total = new InsuranceBilling(new BigDecimal("0.60"))
                .calculate(appointmentForPatientAged(40), CONSULTATION, bill);

        // 8000 covered 60% = 4800; payable 3200; VAT 256; total 3456
        assertEquals(new BigDecimal("4800.00"), bill.getDiscountAmount());
        assertEquals(new BigDecimal("3456.00"), total);
    }

    @Test
    @DisplayName("An out-of-range coverage rate is rejected")
    void invalidCoverageRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new InsuranceBilling(new BigDecimal("1.5")));
        assertThrows(IllegalArgumentException.class,
                () -> new InsuranceBilling(new BigDecimal("-0.1")));
    }
}
