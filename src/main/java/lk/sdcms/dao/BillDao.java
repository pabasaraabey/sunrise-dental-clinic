package lk.sdcms.dao;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.model.Bill;
import lk.sdcms.model.PaymentMethod;
import lk.sdcms.model.PaymentStatus;
import lk.sdcms.util.DBConnectionManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.Optional;

public class BillDao {

    private static final String SELECT_BASE = """
            SELECT bill_id, appointment_no, consultation_fee, treatment_cost,
                   discount_amount, tax_amount, total_amount, payment_status,
                   payment_method, issued_by, issued_date
            FROM bills
            """;

    public Optional<Bill> findById(String billId) {
        String sql = SELECT_BASE + " WHERE bill_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, billId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up bill " + billId, e);
        }
    }

    public Optional<Bill> findByAppointmentNo(String appointmentNo) {
        String sql = SELECT_BASE + " WHERE appointment_no = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, appointmentNo);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up bill for " + appointmentNo, e);
        }
    }

    public void insert(Connection conn, Bill bill)
            throws SQLIntegrityConstraintViolationException {

        String sql = """
                INSERT INTO bills
                    (bill_id, appointment_no, consultation_fee, treatment_cost,
                     discount_amount, tax_amount, total_amount,
                     payment_status, payment_method, issued_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bill.getBillId());
            ps.setString(2, bill.getAppointmentNo());
            // setBigDecimal throughout — the column is DECIMAL and the value
            // must survive the round trip without binary rounding.
            ps.setBigDecimal(3, bill.getConsultationFee());
            ps.setBigDecimal(4, bill.getTreatmentCost());
            ps.setBigDecimal(5, bill.getDiscountAmount());
            ps.setBigDecimal(6, bill.getTaxAmount());
            ps.setBigDecimal(7, bill.getTotalAmount());
            ps.setString(8, bill.getPaymentStatus().name());

            if (bill.getPaymentMethod() == null) {
                ps.setNull(9, Types.VARCHAR);
            } else {
                ps.setString(9, bill.getPaymentMethod().name());
            }
            ps.setLong(10, bill.getIssuedBy());

            ps.executeUpdate();

        } catch (SQLIntegrityConstraintViolationException e) {
            // Unique constraint on appointment_no — a bill already exists.
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert bill", e);
        }
    }

    /** Next sequence number for the day, used to build the bill identifier. */
    public int nextSequenceForDate(Connection conn, LocalDate date) {
        String sql = "SELECT COUNT(*) FROM bills WHERE DATE(issued_date) = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 1;
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to compute bill sequence", e);
        }
    }

    public void updatePayment(String billId, PaymentStatus status, PaymentMethod method) {
        String sql = "UPDATE bills SET payment_status = ?, payment_method = ? WHERE bill_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            if (method == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, method.name());
            }
            ps.setString(3, billId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to update payment", e);
        }
    }

    private Bill mapRow(ResultSet rs) throws SQLException {
        Bill b = new Bill();
        b.setBillId(rs.getString("bill_id"));
        b.setAppointmentNo(rs.getString("appointment_no"));
        b.setConsultationFee(rs.getBigDecimal("consultation_fee"));
        b.setTreatmentCost(rs.getBigDecimal("treatment_cost"));
        b.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        b.setTaxAmount(rs.getBigDecimal("tax_amount"));
        b.setTotalAmount(rs.getBigDecimal("total_amount"));
        b.setPaymentStatus(PaymentStatus.valueOf(rs.getString("payment_status")));

        String method = rs.getString("payment_method");
        if (method != null) {
            b.setPaymentMethod(PaymentMethod.valueOf(method));
        }
        b.setIssuedBy(rs.getLong("issued_by"));

        Timestamp issued = rs.getTimestamp("issued_date");
        if (issued != null) {
            b.setIssuedDate(issued.toLocalDateTime());
        }
        return b;
    }
}
