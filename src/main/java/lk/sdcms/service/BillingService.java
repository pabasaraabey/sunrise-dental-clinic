package lk.sdcms.service;

import lk.sdcms.dao.AppointmentDao;
import lk.sdcms.dao.BillDao;
import lk.sdcms.exception.*;
import lk.sdcms.model.*;
import lk.sdcms.pattern.BillingStrategy;
import lk.sdcms.pattern.SeniorCitizenBilling;
import lk.sdcms.pattern.StandardBilling;
import lk.sdcms.util.DBConnectionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Produces bills for completed appointments.
 *
 * <p>Contains <b>no pricing arithmetic</b>. It selects a {@link BillingStrategy}
 * and delegates. That separation is what lets the clinic introduce corporate
 * rates or insurance settlement later without this class being reopened.
 */
public class BillingService {

    /** Flat fee applied to every visit, irrespective of treatment. */
    public static final BigDecimal CONSULTATION_FEE = new BigDecimal("1500.00");

    private static final DateTimeFormatter BILL_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BillDao        billDao;
    private final AppointmentDao appointmentDao;

    public BillingService(BillDao billDao, AppointmentDao appointmentDao) {
        this.billDao        = billDao;
        this.appointmentDao = appointmentDao;
    }

    /**
     * Generates and stores the bill for an appointment.
     *
     * <p>Writing the bill and marking the appointment completed happen in one
     * transaction. Were they separate and the second failed, the clinic would
     * hold a bill for an appointment still shown as scheduled — the kind of
     * inconsistency that surfaces weeks later during reconciliation.
     */
    public Bill generateBill(String appointmentNo, Long issuedBy) {

        Appointment appointment = appointmentDao.findByNo(appointmentNo)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentNo));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ValidationException("A cancelled appointment cannot be billed");
        }

        // The unique constraint on appointment_no would catch this anyway; the
        // check produces a clearer message in the ordinary case.
        if (billDao.findByAppointmentNo(appointmentNo).isPresent()) {
            throw new ValidationException(
                    "A bill has already been issued for " + appointmentNo);
        }

        Bill bill = new Bill();
        bill.setAppointmentNo(appointmentNo);
        bill.setIssuedBy(issuedBy);
        bill.setPaymentStatus(PaymentStatus.PENDING);
        bill.setIssuedDate(LocalDateTime.now());

        BillingStrategy strategy = selectStrategy(appointment);
        BigDecimal total = strategy.calculate(appointment, CONSULTATION_FEE, bill);
        bill.setTotalAmount(total);

        Connection conn = null;
        try {
            conn = DBConnectionManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            bill.setBillId(buildBillId(conn, LocalDate.now()));

            try {
                billDao.insert(conn, bill);
            } catch (SQLIntegrityConstraintViolationException e) {
                conn.rollback();
                throw new ValidationException(
                        "A bill has already been issued for " + appointmentNo);
            }

            appointmentDao.updateStatus(conn, appointmentNo, AppointmentStatus.COMPLETED);

            conn.commit();
            return bill;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new DataAccessException("Bill generation failed", e);

        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;

        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * Chooses the pricing rule for this patient.
     *
     * <p>This method is the only place that knows which strategies exist. Adding
     * insurance settlement means adding one branch here and one new class —
     * nothing else in the system changes.
     */
    public BillingStrategy selectStrategy(Appointment appointment) {
        Patient patient = appointment.getPatient();

        if (patient.isSeniorCitizen()) {
            return new SeniorCitizenBilling();
        }
        return new StandardBilling();
    }

    public Bill findByAppointmentNo(String appointmentNo) {
        return billDao.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> new ResourceNotFoundException("Bill for", appointmentNo));
    }

    public Bill findById(String billId) {
        return billDao.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
    }

    public void recordPayment(String billId, PaymentMethod method) {
        Bill bill = findById(billId);

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ValidationException("This bill has already been paid");
        }
        billDao.updatePayment(billId, PaymentStatus.PAID, method);
    }

    // -----------------------------------------------------------------

    private String buildBillId(Connection conn, LocalDate date) {
        int sequence = billDao.nextSequenceForDate(conn, date);
        return "BILL-" + date.format(BILL_DATE) + "-" + String.format("%04d", sequence);
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // The original failure is the one worth reporting.
            }
        }
    }

    /** Restores autocommit so the pooled connection is safe for the next borrower. */
    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
