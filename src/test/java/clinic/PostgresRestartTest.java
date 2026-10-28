package clinic;

import clinic.ClinicApplication;
import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class PostgresRestartTest {
    @TempDir Path directory;
    @Test void bookingAndAccountSurviveApplicationAndDatabaseRestart() throws Exception {
        Path data = directory.resolve("postgres-data");
        long appointmentId;
        String email = "restart-test@example.com";
        try (var pg = startDatabase(data); var app = startApplication(pg)) {
            var service = app.getBean(BookingService.class);
            var users = app.getBean(UserDetailsService.class);
            var doctor = (AccountPrincipal) users.loadUserByUsername("maya.chen@doctor.example");
            var patient = (AccountPrincipal) users.loadUserByUsername("sofia.reyes@example.com");
            var time = LocalDateTime.now().plusDays(40).withNano(0);
            long slot = service.createSlot(doctor, 1, time, time.plusMinutes(30));
            appointmentId = service.book(patient, slot, 1).id();
            app.getBean(JdbcTemplate.class).update("INSERT INTO users(email,password_hash,full_name,role) VALUES(?,?,?,?)",
                    email, patient.getPassword(), "Restart Test", "CUSTOMER");
        }
        assertTrue(Files.exists(data.resolve("PG_VERSION")));
        // Both the app AND native PostgreSQL have stopped. Reopen the same data directory.
        try (var pg = startDatabase(data); var app = startApplication(pg)) {
            var jdbc = app.getBean(JdbcTemplate.class);
            assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, appointmentId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?", Integer.class, email));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE delivered_at IS NULL", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE", Integer.class));
        }
    }
    private EmbeddedPostgres startDatabase(Path path) throws Exception {
        return EmbeddedPostgres.builder().setDataDirectory(path.toFile()).setCleanDataDirectory(false)
                .setServerConfig("fsync", "on").setServerConfig("synchronous_commit", "on").start();
    }
    private ConfigurableApplicationContext startApplication(EmbeddedPostgres pg) {
        return new SpringApplicationBuilder(ClinicApplication.class).run(
                "--server.port=0", "--spring.profiles.active=postgres",
                "--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"),
                "--spring.datasource.username=postgres", "--spring.datasource.password=postgres",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.sql.init.mode=never", "--spring.flyway.enabled=true",
                "--notifications.token=test-token", "--notifications.delivery-enabled=false");
    }
}
