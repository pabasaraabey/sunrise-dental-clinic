package lk.sdcms.dao;

import lk.sdcms.model.*;
import lk.sdcms.util.DBConnectionManager;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the constraint that the entire system depends on.
 *
 * <p>Runs against real MySQL rather than an in-memory database. H2 and MySQL
 * differ in how they enforce constraints, and a test that passes against an
 * approximation of the production database proves nothing about the production
 * database. Since this constraint is the one guarantee against double booking,
 * the approximation is not good enough.
 *
 * <p>Requires {@code sdcms_test} to exist with the schema loaded, and
 * db.properties pointing at it while these run.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppointmentDaoConstraintTest {

    private static final LocalDate TEST_DATE = LocalDate.now().plusDays(7);
    private static final LocalTime TEST_TIME = LocalTime.of(10, 0);

    private AppointmentDao appointmentDao;
    private Long dentistId;
    private Long patientId;
    private Long treatmentId;
    private Long createdBy;

    @BeforeEach
    void setUp() throws SQLException {
        appointmentDao = new AppointmentDao();

        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            // Clear anything left by a previous run so tests are independent.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM appointments WHERE appointment_date = ?")) {
                ps.setDate(1, Date.valueOf(TEST_DATE));
                ps.executeUpdate();
            }

            dentistId   = firstId(conn, "SELECT dentist_id FROM dentists LIMIT 1");
            patientId   = firstId(conn, "SELECT patient_id FROM patients LIMIT 1");
            treatmentId = firstId(conn, "SELECT treatment_id FROM treatments LIMIT 1");
            createdBy   = firstId(conn, "SELECT user_id FROM users LIMIT 1");
        }
    }

    private Long firstId(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "Seed data missing — run schema.sql and data.sql");
            return rs.getLong(1);
        }
    }

    @Test
    @Order(1)
    @DisplayName("A free slot reports as available")
    void freeSlotIsAvailable() {
        assertFalse(appointmentDao.existsForSlot(dentistId, TEST_DATE, TEST_TIME));
    }

    @Test
    @Order(2)
    @DisplayName("An occupied slot reports as taken")
    void occupiedSlotIsDetected() throws Exception {
        insertAppointment("APT-TEST-0001");
        assertTrue(appointmentDao.existsForSlot(dentistId, TEST_DATE, TEST_TIME));
    }

    /**
     * The important one. Inserts a duplicate directly through the DAO,
     * bypassing any service-layer checking entirely, and asserts the database
     * itself refuses it. If this passes only because the service checked
     * first, the system has no real protection.
     */
    @Test
    @Order(3)
    @DisplayName("The database rejects a duplicate slot even with no service check")
    void databaseConstraintRejectsDuplicate() throws Exception {
        insertAppointment("APT-TEST-0002");

        SQLIntegrityConstraintViolationException thrown =
                assertThrows(SQLIntegrityConstraintViolationException.class,
                        () -> insertAppointment("APT-TEST-0003"));

        assertTrue(thrown.getMessage().toLowerCase().contains("duplicate"),
                "Expected a duplicate-key violation, got: " + thrown.getMessage());
    }

    /**
     * Proves the race condition is actually closed.
     *
     * <p>Two threads are released simultaneously by a latch so their inserts
     * genuinely overlap. Calling the method twice in sequence would pass
     * trivially and demonstrate nothing — the whole point is that both threads
     * pass the availability check before either commits.
     */
    @Test
    @Order(4)
    @DisplayName("Under concurrent booking, exactly one insert succeeds")
    void concurrentBookingAllowsExactlyOne() throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes  = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch finished = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            final String aptNo = "APT-RACE-000" + i;
            pool.submit(() -> {
                try {
                    startGate.await();          // both threads block here
                    insertAppointment(aptNo);   // then race
                    successes.incrementAndGet();
                } catch (SQLIntegrityConstraintViolationException e) {
                    rejections.incrementAndGet();
                } catch (Exception e) {
                    // Any other failure is a genuine problem, not a rejection.
                } finally {
                    finished.countDown();
                }
            });
        }

        startGate.countDown();                  // release both at once
        assertTrue(finished.await(10, TimeUnit.SECONDS), "Threads did not finish");
        pool.shutdown();

        assertEquals(1, successes.get(),  "Exactly one booking should succeed");
        assertEquals(1, rejections.get(), "Exactly one booking should be rejected");
    }

    private void insertAppointment(String appointmentNo) throws Exception {
        Patient patient = new Patient();
        patient.setPatientId(patientId);

        Dentist dentist = new Dentist();
        dentist.setDentistId(dentistId);

        Treatment treatment = new Treatment();
        treatment.setTreatmentId(treatmentId);

        Appointment appointment = Appointment.builder()
                .appointmentNo(appointmentNo)
                .patient(patient)
                .dentist(dentist)
                .treatment(treatment)
                .date(TEST_DATE)
                .time(TEST_TIME)
                .createdBy(createdBy)
                .build();

        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            appointmentDao.insert(conn, appointment);
        }
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM appointments WHERE appointment_no LIKE 'APT-TEST%'"
                   + " OR appointment_no LIKE 'APT-RACE%'")) {
            ps.executeUpdate();
        }
    }
}
