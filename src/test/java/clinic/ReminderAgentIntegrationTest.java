package clinic;

import clinic.ai.*;
import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReminderAgentIntegrationTest {
    @Autowired BookingService booking;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserDetailsService users;
    @Autowired ReminderStore store;
    @Autowired MeterRegistry metrics;
    @MockBean AiModel model;
    ReminderAgent agent;
    AccountPrincipal patient() { return (AccountPrincipal) users.loadUserByUsername("sofia.reyes@example.com"); }
    @BeforeEach void clean() {
        reset(model);
        jdbc.update("DELETE FROM ai_reminders"); jdbc.update("DELETE FROM notification_outbox");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("UPDATE availability_slots SET status='AVAILABLE',version=0 WHERE id IN (1,2,3)");
        agent = new ReminderAgent(model, store, metrics, true, 24, 20);
    }
    long book(int slot, int service, LocalDateTime start) {
        jdbc.update("UPDATE availability_slots SET starts_at=?,ends_at=? WHERE id=?", start, start.plusMinutes(30), slot);
        return booking.book(patient(), slot, service).id();
    }
    @Test void scheduledAgentUsesLlmAndOutboxAndDoesNotRegenerateAfterRestart() {
        long appointment = book(1, 1, LocalDateTime.now().plusHours(3));
        when(model.generate(anyString(), anyString())).thenReturn("Hi Sofia, a friendly reminder about your upcoming visit.");
        double before = metrics.counter("appointments.cancelled").count();
        assertEquals(1, agent.run().queued());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE type='APPOINTMENT_REMINDER'", Integer.class));
        assertEquals("ENQUEUED", jdbc.queryForObject("SELECT status FROM ai_reminders WHERE appointment_id=?", String.class, appointment));
        assertEquals(0, new ReminderAgent(model, store, metrics, true, 24, 20).run().queued());
        verify(model, times(1)).generate(anyString(), contains("Sofia"));
        assertEquals(before, metrics.counter("appointments.cancelled").count());
    }
    @Test void failuresRetainRetryableWorkAndCancellationDuringGenerationPreventsReminder() {
        long appointment = book(1, 1, LocalDateTime.now().plusHours(3));
        when(model.generate(anyString(), anyString())).thenThrow(new IllegalStateException("provider unavailable"));
        assertEquals(1, agent.run().failed());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE type='APPOINTMENT_REMINDER'", Integer.class));
        jdbc.update("UPDATE ai_reminders SET lease_until=?", LocalDateTime.now().minusMinutes(1));
        doAnswer(call -> {
            booking.cancel(patient(), appointment);
            return "Hi Sofia!";
        }).when(model).generate(anyString(), anyString());
        assertEquals(0, agent.run().queued());
        assertEquals("SKIPPED", jdbc.queryForObject("SELECT status FROM ai_reminders WHERE appointment_id=?", String.class, appointment));
    }
    @Test void concurrentRunsQueueOnlyOneReminderAndClaimCanRecoverAfterCrash() throws Exception {
        book(1, 1, LocalDateTime.now().plusHours(3));
        when(model.generate(anyString(), anyString())).thenReturn("Hi Sofia!");
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { barrier.await(); return agent.run(); });
            var two = pool.submit(() -> { barrier.await(); return agent.run(); });
            assertEquals(1, one.get(15, TimeUnit.SECONDS).queued() + two.get(15, TimeUnit.SECONDS).queued());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE type='APPOINTMENT_REMINDER'", Integer.class));
        long second = book(2, 2, LocalDateTime.now().plusHours(5));
        assertTrue(store.claim(second)); // Simulate a worker dying before generation/queueing.
        assertEquals(0, agent.run().queued());
        jdbc.update("UPDATE ai_reminders SET lease_until=? WHERE appointment_id=?", LocalDateTime.now().minusMinutes(1), second);
        assertEquals(1, agent.run().queued());
    }
    @Test void outOfWindowPastAndCancelledAppointmentsAreExcluded() {
        long cancelled = book(1, 1, LocalDateTime.now().plusHours(2));
        booking.cancel(patient(), cancelled);
        book(2, 2, LocalDateTime.now().plusHours(26));
        book(3, 3, LocalDateTime.now().plusHours(3));
        var end = LocalDateTime.now().minusHours(1);
        jdbc.update("UPDATE availability_slots SET starts_at=?,ends_at=? WHERE id=3", end.minusMinutes(30), end);
        assertEquals(0, agent.run().scanned());
        verifyNoInteractions(model);
    }
}
