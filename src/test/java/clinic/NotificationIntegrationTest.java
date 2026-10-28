package clinic;

import clinic.notification.NotificationDispatcher;
import clinic.notification.NotificationRequest;
import clinic.notification.HttpNotificationGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import clinic.repository.BookingRepository;
import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"notifications.delivery-enabled=true", "notifications.timeout-ms=150",
                "notifications.max-attempts=2", "notifications.backoff-ms=0"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class NotificationIntegrationTest {
    static HttpServer stub;
    static ExecutorService executor;
    static final AtomicInteger calls = new AtomicInteger(), visibleBookings = new AtomicInteger();
    static final AtomicReference<String> mode = new AtomicReference<>("success");
    static final AtomicReference<JdbcTemplate> database = new AtomicReference<>();
    @DynamicPropertySource
    static void stubUrl(DynamicPropertyRegistry registry) throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); stub.setExecutor(executor);
        stub.createContext("/notify", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            visibleBookings.set(database.get().queryForObject("SELECT COUNT(*) FROM appointments", Integer.class));
            try {
                if (mode.get().equals("slow")) Thread.sleep(500);
                exchange.sendResponseHeaders(mode.get().equals("error") ? 503 : mode.get().equals("bad") ? 400 : 200, -1);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        stub.start();
        registry.add("notifications.url", () -> "http://127.0.0.1:" + stub.getAddress().getPort() + "/notify");
    }
    @AfterAll static void stop() { stub.stop(0); executor.shutdownNow(); }
    @Autowired BookingService service;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserDetailsService users;
    @Autowired TestRestTemplate http;
    @Autowired MeterRegistry metrics;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;
    @SpyBean BookingRepository repository;
    AccountPrincipal patient() { return (AccountPrincipal) users.loadUserByUsername("sofia.reyes@example.com"); }
    @BeforeEach void clean() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        reset(repository); database.set(jdbc); calls.set(0); visibleBookings.set(0); mode.set("success");
        jdbc.update("DELETE FROM notification_outbox"); jdbc.update("DELETE FROM notification_receipts");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("UPDATE availability_slots SET status='AVAILABLE', version=0 WHERE id=1");
        jdbc.update("UPDATE availability_slots SET starts_at=DATEADD('DAY',1,CURRENT_DATE) + TIME '08:00:00', " +
                "ends_at=DATEADD('DAY',1,CURRENT_DATE) + TIME '08:30:00' WHERE id=1");
    }
    @Test void successNotifiesOnlyAfterBookingIsCommitted() {
        double before = metrics.counter("appointments.booked").count();
        var appointment = service.book(patient(), 1, 1);
        assertEquals("BOOKED", appointment.status()); assertEquals(1, visibleBookings.get());
        assertEquals(1, calls.get());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE delivered_at IS NOT NULL", Integer.class));
        assertEquals(before + 1, metrics.counter("appointments.booked").count());
    }
    @Test void unavailableNotificationDoesNotUndoBookingAndRecoversLater() {
        mode.set("error");
        var appointment = service.book(patient(), 1, 1);
        assertEquals("BOOKED", repository.appointment(appointment.id()).orElseThrow().status());
        assertEquals(2, calls.get());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE delivered_at IS NULL", Integer.class));
        mode.set("success"); jdbc.update("UPDATE notification_outbox SET next_attempt_at=CURRENT_TIMESTAMP");
        dispatcher.recoverPending();
        assertEquals(3, calls.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE delivered_at IS NULL", Integer.class));
    }
    @Test void slowNotificationTimesOutWithoutFailingBooking() {
        mode.set("slow");
        assertDoesNotThrow(() -> service.book(patient(), 1, 1));
        assertEquals(2, calls.get());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE delivered_at IS NULL", Integer.class));
    }
    @Test void failedBookingDoesNotNotifyOrSaveOutboxEvent() {
        doThrow(new DataIntegrityViolationException("test failure")).when(repository).insertAppointment(anyLong(), any());
        double before = metrics.counter("appointments.booked").count();
        assertThrows(ResponseStatusException.class, () -> service.book(patient(), 1, 1));
        assertEquals(0, calls.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class));
        assertEquals(before, metrics.counter("appointments.booked").count());
        assertEquals("AVAILABLE", repository.slot(1).orElseThrow().status());
    }
    @Test void cancellationNotifiesAfterCommitEvenWhenRemoteFails() {
        long id = service.book(patient(), 1, 1).id();
        mode.set("error"); service.cancel(patient(), id);
        assertEquals("CANCELLED", repository.appointment(id).orElseThrow().status());
        assertEquals("AVAILABLE", repository.slot(1).orElseThrow().status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox " +
                "WHERE type='BOOKING_CANCELLED' AND delivered_at IS NULL", Integer.class));
    }
    @Test void rollbackAfterEnqueueDiscardsTheQueuedEventToo() {
        doThrow(new IllegalStateException("failure after enqueue")).when(repository).appointment(anyLong());
        assertThrows(IllegalStateException.class, () -> service.book(patient(), 1, 1));
        assertEquals(0, calls.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class));
    }
    @Test void invalidRemoteRequestIsNotRetriedInTheForeground() {
        mode.set("bad");
        assertDoesNotThrow(() -> service.book(patient(), 1, 1));
        assertEquals(1, calls.get());
    }
    @Test void realHttpGatewayCanCallAndRetryTheMockControllerIdempotently() {
        var gateway = new HttpNotificationGateway(mapper, "http://localhost:" + port + "/api/mock-notifications",
                "test-token", 2, 1000, 0);
        var request = new NotificationRequest(UUID.randomUUID(), "patient@example.com",
                NotificationRequest.Type.BOOKING_CONFIRMED, "Confirmed");
        assertTrue(gateway.send(request)); assertTrue(gateway.send(request));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_receipts", Integer.class));
    }
    @Test void mockReceiverDeduplicatesEventsAndRejectsChangedPayload() {
        UUID id = UUID.randomUUID();
        var request = new NotificationRequest(id, "patient@example.com", NotificationRequest.Type.BOOKING_CONFIRMED, "Confirmed");
        assertEquals(200, post(request, "test-token").getStatusCode().value());
        var duplicate = post(request, "test-token");
        assertEquals(200, duplicate.getStatusCode().value()); assertTrue(duplicate.getBody().contains("\"duplicate\":true"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_receipts", Integer.class));
        var changed = new NotificationRequest(id, "patient@example.com", request.type(), "Different message");
        assertEquals(409, post(changed, "test-token").getStatusCode().value());
        assertEquals(401, post(request, "wrong-token").getStatusCode().value());
    }
    ResponseEntity<String> post(NotificationRequest request, String token) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Notification-Token", token);
        return http.postForEntity("/api/mock-notifications", new HttpEntity<>(request, headers), String.class);
    }
    @Test void healthIsPublicButMetricsRequireAdmin() throws Exception {
        var health = http.getForEntity("/health", String.class);
        assertEquals(200, health.getStatusCode().value()); assertTrue(health.getBody().contains("\"database\":\"UP\""));
        // Unauthorized actuator requests redirect to login; an anonymous caller cannot read the metric.
        var metricsResponse = http.getForEntity("/actuator/metrics/appointments.booked", String.class);
        assertFalse(metricsResponse.getBody() != null && metricsResponse.getBody().contains("\"measurements\""));
        mvc.perform(get("/actuator/metrics/appointments.booked").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("appointments.booked"));
        mvc.perform(get("/actuator/metrics/appointments.booked").with(user("patient").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }
}
