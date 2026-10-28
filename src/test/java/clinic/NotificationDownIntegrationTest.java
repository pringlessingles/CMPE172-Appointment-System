package clinic;

import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.ServerSocket;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"notifications.delivery-enabled=true", "notifications.max-attempts=2"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationDownIntegrationTest {
    @DynamicPropertySource static void downService(DynamicPropertyRegistry registry) throws Exception {
        // Reserve then close a local port: no notification stub is listening there.
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        registry.add("notifications.url", () -> "http://127.0.0.1:" + port + "/notify");
    }
    @Autowired BookingService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserDetailsService users;
    @Test void bookingSucceedsWhenNotificationServiceIsActuallyDown() {
        var patient = (AccountPrincipal) users.loadUserByUsername("sofia.reyes@example.com");
        var appointment = service.book(patient, 1, 1);
        assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, appointment.id()));
        assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM availability_slots WHERE id=1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE delivered_at IS NULL AND attempts=1", Integer.class));
    }
}
