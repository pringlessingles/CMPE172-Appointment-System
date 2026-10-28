package clinic;

import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.LocalDateTime;
import java.io.IOException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "notifications.delivery-enabled=false")
@ActiveProfiles("postgres")
class PostgresPersistenceTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws IOException { PostgresTestServer.configure(registry); }
    @AfterAll static void stop() throws IOException { PostgresTestServer.close(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingService service;
    @Autowired UserDetailsService users;
    @Autowired Flyway flyway;
    AccountPrincipal patient(String email) { return (AccountPrincipal) users.loadUserByUsername(email); }
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM notification_outbox");
        jdbc.update("DELETE FROM notification_receipts");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots WHERE id > 7");
        jdbc.update("UPDATE availability_slots SET status=CASE WHEN id=7 THEN 'BLOCKED' ELSE 'AVAILABLE' END, version=0");
    }
    @Test void postgresMigrationsBookingCancellationAndRepeatMigrationKeepData() {
        var doctor = patient("maya.chen@doctor.example");
        var start = LocalDateTime.now().plusDays(20).withNano(0);
        long slot = service.createSlot(doctor, 1, start, start.plusMinutes(30));
        var owner = patient("sofia.reyes@example.com");
        long id = service.book(owner, slot, 1).id();
        assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, id));
        service.cancel(owner, id);
        long replacement = service.book(patient("alex.morgan@example.com"), slot, 1).id();
        flyway.migrate(); // A repeat startup migration must not drop/reseed existing rows.
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, id));
        assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, replacement));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE message LIKE ?", Integer.class, "Appointment %"));
    }
    @Test void databaseRejectsOverlappingSlotsEvenWhenServiceIsBypassed() {
        var start = LocalDateTime.now().plusDays(25).withNano(0);
        service.createSlot(patient("maya.chen@doctor.example"), 1, start, start.plusMinutes(30));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at)
                VALUES(1,2,?,?)
                """, start.plusMinutes(15), start.plusMinutes(45)));
        assertDoesNotThrow(() -> service.createSlot(patient("maya.chen@doctor.example"), 2,
                start.plusMinutes(30), start.plusMinutes(60)));
        assertDoesNotThrow(() -> service.createSlot(patient("jordan.lee@doctor.example"), 3,
                start.plusMinutes(15), start.plusMinutes(45)));
    }
    @Test void simultaneousDoctorRequestsCannotCreateOverlappingSlots() throws Exception {
        var gate = new CyclicBarrier(2);
        var start = LocalDateTime.now().plusDays(30).withNano(0);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { gate.await(); return tryCreate(start); });
            var second = pool.submit(() -> { gate.await(); return tryCreate(start.plusMinutes(15)); });
            int a = first.get(15, TimeUnit.SECONDS), b = second.get(15, TimeUnit.SECONDS);
            assertTrue((a == 201 && b == 409) || (a == 409 && b == 201));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM availability_slots WHERE id > 7", Integer.class));
        }
    }
    int tryCreate(LocalDateTime start) {
        try { service.createSlot(patient("maya.chen@doctor.example"), 1, start, start.plusMinutes(30)); return 201; }
        catch (org.springframework.web.server.ResponseStatusException e) { return e.getStatusCode().value(); }
    }
}
