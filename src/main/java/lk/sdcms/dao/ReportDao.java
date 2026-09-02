package lk.sdcms.dao;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.util.DBConnectionManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate queries behind the clinic's reports.
 *
 * <p>Aggregation happens in SQL — {@code GROUP BY}, {@code SUM}, {@code COUNT}
 * — rather than by pulling rows into Java and totalling them in a loop. The
 * database is built for this, and doing it in Java would mean transferring
 * every row across the connection to produce a handful of numbers.
 *
 * <p>Results are returned as ordered maps rather than typed objects because
 * each report has a different shape and a class per report would be a class
 * per report with no behaviour in it.
 */
public class ReportDao {

    /** Counters for the dashboard. One round trip, not five. */
    public Map<String, Object> dashboardSummary(LocalDate date) {
        String sql = """
                SELECT
                    COUNT(*)                                                  AS total,
                    SUM(status = 'SCHEDULED')                                 AS scheduled,
                    SUM(status = 'COMPLETED')                                 AS completed,
                    SUM(status = 'CANCELLED')                                 AS cancelled,
                    SUM(status = 'NO_SHOW')                                   AS noShow
                FROM appointments
                WHERE appointment_date = ?
                """;

        Map<String, Object> summary = new LinkedHashMap<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(date));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.put("total",     rs.getInt("total"));
                    summary.put("scheduled", rs.getInt("scheduled"));
                    summary.put("completed", rs.getInt("completed"));
                    summary.put("cancelled", rs.getInt("cancelled"));
                    summary.put("noShow",    rs.getInt("noShow"));
                }
            }

            summary.put("revenueToday", revenueOn(conn, date));
            summary.put("patientsTotal", countOf(conn, "SELECT COUNT(*) FROM patients"));
            summary.put("dentistsActive",
                    countOf(conn, "SELECT COUNT(*) FROM dentists WHERE is_active = TRUE"));

            // Month-to-date, so the dashboard shows a trend rather than a
            // single day that might be unrepresentative.
            LocalDate monthStart = date.withDayOfMonth(1);

            summary.put("bookingsThisMonth", countBetween(conn,
                    "SELECT COUNT(*) FROM appointments WHERE appointment_date BETWEEN ? AND ?",
                    monthStart, date));

            summary.put("newPatientsThisMonth", countBetween(conn,
                    "SELECT COUNT(*) FROM patients WHERE registered_date BETWEEN ? AND ?",
                    monthStart, date));

            summary.put("revenueThisMonth", sumBetween(conn,
                    """
                    SELECT COALESCE(SUM(total_amount), 0) FROM bills
                    WHERE DATE(issued_date) BETWEEN ? AND ?
                    """, monthStart, date));

            summary.put("unbilledCompleted", countOf(conn,
                    """
                    SELECT COUNT(*) FROM appointments a
                    LEFT JOIN bills b ON b.appointment_no = a.appointment_no
                    WHERE a.status = 'COMPLETED' AND b.bill_id IS NULL
                    """));

            return summary;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to build dashboard summary", e);
        }
    }

    /**
     * Report 2 — how the workload is distributed across dentists.
     * Helps the clinic see whether one dentist is carrying the day.
     */
    public List<Map<String, Object>> dentistWorkload(LocalDate from, LocalDate to) {
        String sql = """
                SELECT d.full_name                                    AS dentist,
                       d.specialization                               AS specialization,
                       COUNT(a.appointment_no)                        AS appointments,
                       SUM(a.status = 'COMPLETED')                    AS completed,
                       SUM(a.status = 'CANCELLED')                    AS cancelled,
                       SUM(a.status = 'NO_SHOW')                      AS noShow,
                       COALESCE(SUM(t.duration_minutes), 0)           AS bookedMinutes
                FROM dentists d
                LEFT JOIN appointments a
                       ON a.dentist_id = d.dentist_id
                      AND a.appointment_date BETWEEN ? AND ?
                LEFT JOIN treatments t ON t.treatment_id = a.treatment_id
                WHERE d.is_active = TRUE
                GROUP BY d.dentist_id, d.full_name, d.specialization
                ORDER BY appointments DESC, d.full_name
                """;
        // LEFT JOIN so a dentist with no bookings still appears with zero —
        // an INNER JOIN would silently hide the idle dentist, which is exactly
        // the one the clinic needs to know about.
        return queryRange(sql, from, to,
                "dentist", "specialization", "appointments",
                "completed", "cancelled", "noShow", "bookedMinutes");
    }

    /**
     * Report 3 — revenue by day, with the tax and discount split out.
     * Reads from bills rather than recalculating from current prices, so a
     * later price change never rewrites past income.
     */
    public List<Map<String, Object>> revenueByDay(LocalDate from, LocalDate to) {
        String sql = """
                SELECT DATE(b.issued_date)          AS day,
                       COUNT(*)                     AS bills,
                       SUM(b.consultation_fee)      AS consultationTotal,
                       SUM(b.treatment_cost)        AS treatmentTotal,
                       SUM(b.discount_amount)       AS discountTotal,
                       SUM(b.tax_amount)            AS taxTotal,
                       SUM(b.total_amount)          AS grandTotal
                FROM bills b
                WHERE DATE(b.issued_date) BETWEEN ? AND ?
                GROUP BY DATE(b.issued_date)
                ORDER BY day DESC
                """;
        return queryRange(sql, from, to,
                "day", "bills", "consultationTotal", "treatmentTotal",
                "discountTotal", "taxTotal", "grandTotal");
    }

    /** Revenue grouped by month, for a longer view. */
    public List<Map<String, Object>> revenueByMonth(LocalDate from, LocalDate to) {
        String sql = """
                SELECT DATE_FORMAT(b.issued_date, '%Y-%m') AS month,
                       COUNT(*)                            AS bills,
                       SUM(b.discount_amount)              AS discountTotal,
                       SUM(b.tax_amount)                   AS taxTotal,
                       SUM(b.total_amount)                 AS grandTotal
                FROM bills b
                WHERE DATE(b.issued_date) BETWEEN ? AND ?
                GROUP BY DATE_FORMAT(b.issued_date, '%Y-%m')
                ORDER BY month DESC
                """;
        return queryRange(sql, from, to,
                "month", "bills", "discountTotal", "taxTotal", "grandTotal");
    }

    /**
     * Report 4 — which treatments the clinic actually performs.
     * Informs stock ordering and which specialisations to staff for.
     */
    public List<Map<String, Object>> treatmentPopularity(LocalDate from, LocalDate to) {
        String sql = """
                SELECT t.name                                   AS treatment,
                       t.base_cost                              AS currentPrice,
                       COUNT(a.appointment_no)                  AS timesBooked,
                       SUM(a.status = 'COMPLETED')              AS completed,
                       COALESCE(SUM(b.total_amount), 0)         AS revenue
                FROM treatments t
                LEFT JOIN appointments a
                       ON a.treatment_id = t.treatment_id
                      AND a.appointment_date BETWEEN ? AND ?
                LEFT JOIN bills b ON b.appointment_no = a.appointment_no
                GROUP BY t.treatment_id, t.name, t.base_cost
                HAVING timesBooked > 0
                ORDER BY timesBooked DESC, revenue DESC
                """;
        return queryRange(sql, from, to,
                "treatment", "currentPrice", "timesBooked", "completed", "revenue");
    }

    /**
     * Report 6 — cancellations and no-shows by dentist.
     * Identifies revenue leaking through slots that were held but unused.
     */
    public List<Map<String, Object>> noShowAndCancellation(LocalDate from, LocalDate to) {
        String sql = """
                SELECT d.full_name                                          AS dentist,
                       COUNT(a.appointment_no)                              AS total,
                       SUM(a.status = 'CANCELLED')                          AS cancelled,
                       SUM(a.status = 'NO_SHOW')                            AS noShow,
                       ROUND(100 * SUM(a.status IN ('CANCELLED','NO_SHOW'))
                             / NULLIF(COUNT(a.appointment_no), 0), 1)       AS lossRate
                FROM dentists d
                LEFT JOIN appointments a
                       ON a.dentist_id = d.dentist_id
                      AND a.appointment_date BETWEEN ? AND ?
                GROUP BY d.dentist_id, d.full_name
                HAVING total > 0
                ORDER BY lossRate DESC
                """;
        // NULLIF guards the division: a dentist with no appointments in the
        // range would otherwise divide by zero.
        return queryRange(sql, from, to,
                "dentist", "total", "cancelled", "noShow", "lossRate");
    }


    /**
     * Booking counts over time, grouped by day, week, month or year.
     *
     * <p>The grouping expression is chosen from a fixed set rather than being
     * interpolated from the request — a caller-supplied fragment inside a SQL
     * string is an injection vector that no PreparedStatement can protect
     * against, because the grouping is structure rather than a value.
     */
    public List<Map<String, Object>> bookingTrend(LocalDate from, LocalDate to,
                                                  String granularity) {
        String bucket = switch (granularity == null ? "day" : granularity) {
            case "week"  -> "DATE_FORMAT(a.appointment_date, '%x-W%v')";
            case "month" -> "DATE_FORMAT(a.appointment_date, '%Y-%m')";
            case "year"  -> "DATE_FORMAT(a.appointment_date, '%Y')";
            default      -> "DATE_FORMAT(a.appointment_date, '%Y-%m-%d')";
        };

        String sql = """
                SELECT %s                                  AS bucket,
                       COUNT(*)                            AS bookings,
                       SUM(a.status = 'COMPLETED')         AS completed,
                       SUM(a.status = 'CANCELLED')         AS cancelled,
                       SUM(a.status = 'NO_SHOW')           AS noShow,
                       COALESCE(SUM(b.total_amount), 0)    AS revenue
                FROM appointments a
                LEFT JOIN bills b ON b.appointment_no = a.appointment_no
                WHERE a.appointment_date BETWEEN ? AND ?
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(bucket);

        return queryRange(sql, from, to,
                "bucket", "bookings", "completed", "cancelled", "noShow", "revenue");
    }

    /**
     * Patient activity — how many visits each has made, and whether they are
     * returning.
     *
     * <p>A patient is identified by contact number, which the schema enforces
     * as unique. Counting distinct appointments per patient is what separates
     * a first-time visitor from a regular, and the clinic cares about the
     * difference when judging whether it is retaining people.
     */
    public List<Map<String, Object>> patientActivity() {
        String sql = """
                SELECT p.patient_id                        AS patientId,
                       p.full_name                         AS patientName,
                       p.contact_no                        AS contactNo,
                       p.date_of_birth                     AS dateOfBirth,
                       p.registered_date                   AS registeredDate,
                       COUNT(a.appointment_no)             AS visits,
                       SUM(a.status = 'COMPLETED')         AS completedVisits,
                       MIN(a.appointment_date)             AS firstVisit,
                       MAX(a.appointment_date)             AS lastVisit,
                       COALESCE(SUM(b.total_amount), 0)    AS totalBilled
                FROM patients p
                LEFT JOIN appointments a ON a.patient_id = p.patient_id
                LEFT JOIN bills b        ON b.appointment_no = a.appointment_no
                GROUP BY p.patient_id, p.full_name, p.contact_no,
                         p.date_of_birth, p.registered_date
                ORDER BY visits DESC, p.full_name
                """;

        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("patientId",       rs.getLong("patientId"));
                row.put("patientName",     rs.getString("patientName"));
                row.put("contactNo",       rs.getString("contactNo"));
                row.put("dateOfBirth",     rs.getDate("dateOfBirth").toLocalDate());
                row.put("registeredDate",  rs.getDate("registeredDate") == null
                        ? null : rs.getDate("registeredDate").toLocalDate());
                row.put("visits",          rs.getInt("visits"));
                row.put("completedVisits", rs.getInt("completedVisits"));

                Date first = rs.getDate("firstVisit");
                Date last  = rs.getDate("lastVisit");
                row.put("firstVisit", first == null ? null : first.toLocalDate());
                row.put("lastVisit",  last  == null ? null : last.toLocalDate());

                row.put("totalBilled", rs.getBigDecimal("totalBilled"));
                // Two or more visits means the clinic kept them.
                row.put("returning", rs.getInt("visits") > 1);

                rows.add(row);
            }
            return rows;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to build patient activity report", e);
        }
    }

    /** Appointments still awaiting an outcome — the receptionist's work queue. */
    public int countPending(LocalDate date) {
        String sql = """
                SELECT COUNT(*) FROM appointments
                WHERE appointment_date = ? AND status = 'SCHEDULED'
                """;

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to count pending appointments", e);
        }
    }

    /** Averages, for the dashboard headline figures. */
    public Map<String, Object> averages(LocalDate from, LocalDate to) {
        String sql = """
                SELECT COUNT(*)                                       AS totalBookings,
                       COUNT(DISTINCT a.appointment_date)             AS activeDays,
                       COUNT(DISTINCT a.patient_id)                   AS distinctPatients,
                       COALESCE(SUM(b.total_amount), 0)               AS totalRevenue
                FROM appointments a
                LEFT JOIN bills b ON b.appointment_no = a.appointment_no
                WHERE a.appointment_date BETWEEN ? AND ?
                """;

        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int bookings   = rs.getInt("totalBookings");
                    int activeDays = rs.getInt("activeDays");

                    result.put("totalBookings",    bookings);
                    result.put("activeDays",       activeDays);
                    result.put("distinctPatients", rs.getInt("distinctPatients"));
                    result.put("totalRevenue",     rs.getBigDecimal("totalRevenue"));

                    // Averaged over days the clinic actually saw patients, not
                    // over the calendar. Dividing by calendar days would drag
                    // the figure down with Sundays and holidays and make the
                    // clinic look quieter than it is.
                    result.put("averagePerDay", activeDays == 0
                            ? java.math.BigDecimal.ZERO
                            : java.math.BigDecimal.valueOf(bookings)
                                .divide(java.math.BigDecimal.valueOf(activeDays),
                                        1, java.math.RoundingMode.HALF_UP));
                }
            }
            return result;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to compute averages", e);
        }
    }

    // -----------------------------------------------------------------

    private java.math.BigDecimal revenueOn(Connection conn, LocalDate date)
            throws SQLException {

        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM bills WHERE DATE(issued_date) = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                // getBigDecimal, never getDouble — this is money.
                return rs.next() ? rs.getBigDecimal(1) : java.math.BigDecimal.ZERO;
            }
        }
    }

    private int countBetween(Connection conn, String sql,
                             LocalDate from, LocalDate to) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private java.math.BigDecimal sumBetween(Connection conn, String sql,
                                            LocalDate from, LocalDate to)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : java.math.BigDecimal.ZERO;
            }
        }
    }

    private int countOf(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private List<Map<String, Object>> queryRange(String sql, LocalDate from,
                                                 LocalDate to, String... columns) {
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String column : columns) {
                        row.put(column, rs.getObject(column));
                    }
                    rows.add(row);
                }
            }
            return rows;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to run report", e);
        }
    }
}
