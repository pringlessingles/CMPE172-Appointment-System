package clinic;

import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "notifications.delivery-enabled=false")
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "PG_TEST_URL", matches = ".+")
class PostgresPersistenceTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("PG_TEST_URL"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingService service;
    @Autowired UserDetailsService users;
    @Autowired Flyway flyway;
    AccountPrincipal patient(String email) { return (AccountPrincipal) users.loadUserByUsername(email); }
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
}
