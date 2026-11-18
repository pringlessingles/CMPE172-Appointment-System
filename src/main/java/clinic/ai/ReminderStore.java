package clinic.ai;

import clinic.notification.NotificationOutbox;
import clinic.notification.NotificationRequest;
import clinic.repository.BookingRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ReminderStore {
    public record Candidate(long appointmentId, long providerId, String recipient, String firstName,
                            String doctor, LocalDateTime startsAt) { }
    private final JdbcTemplate jdbc;
    private final NotificationOutbox outbox;
    private final BookingRepository booking;
    public ReminderStore(JdbcTemplate jdbc, NotificationOutbox outbox, BookingRepository booking) {
        this.jdbc = jdbc; this.outbox = outbox; this.booking = booking;
    }

    public List<Candidate> due(int horizonHours, int batchSize) {
        return jdbc.query("""
                SELECT a.id, a.provider_id, u.email, u.full_name, doctor.full_name AS doctor, s.starts_at
                FROM appointments a JOIN availability_slots s ON s.id=a.availability_slot_id
                JOIN users u ON u.id=a.customer_user_id JOIN providers p ON p.id=a.provider_id
                JOIN users doctor ON doctor.id=p.user_id
                LEFT JOIN ai_reminders r ON r.appointment_id=a.id
                WHERE a.status='BOOKED' AND s.starts_at>CURRENT_TIMESTAMP AND s.starts_at<=?
                AND (r.appointment_id IS NULL OR (r.status='PENDING' AND (r.lease_until IS NULL OR r.lease_until<CURRENT_TIMESTAMP)))
                ORDER BY s.starts_at, a.id LIMIT ?
                """, (rs, n) -> new Candidate(rs.getLong("id"), rs.getLong("provider_id"), rs.getString("email"),
                        rs.getString("full_name").split(" ", 2)[0], rs.getString("doctor"),
                        rs.getTimestamp("starts_at").toLocalDateTime()),
                Timestamp.valueOf(LocalDateTime.now().plusHours(horizonHours)), batchSize);
    }

    // Intentionally auto-commit: a racing duplicate insert must not abort a PostgreSQL transaction.
    public boolean claim(long appointmentId) {
        try { jdbc.update("INSERT INTO ai_reminders(appointment_id) VALUES (?)", appointmentId); }
        catch (DuplicateKeyException ignored) { }
        return jdbc.update("""
                UPDATE ai_reminders SET attempts=attempts+1, lease_until=?
                WHERE appointment_id=? AND status='PENDING' AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)
                """, Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)), appointmentId) == 1;
    }
    public void defer(long appointmentId) {
        jdbc.update("UPDATE ai_reminders SET lease_until=? WHERE appointment_id=? AND status='PENDING'",
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)), appointmentId);
    }

    @Transactional
    public boolean enqueue(Candidate candidate, String greeting) {
        booking.lockProvider(candidate.providerId());
        // Recheck status and time after the external LLM call; cancellation might have happened meanwhile.
        var statuses = jdbc.queryForList("SELECT status FROM ai_reminders WHERE appointment_id=? FOR UPDATE",
                String.class, candidate.appointmentId());
        if (statuses.isEmpty() || !"PENDING".equals(statuses.getFirst())) return false;
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointments a JOIN availability_slots s ON s.id=a.availability_slot_id
                WHERE a.id=? AND a.status='BOOKED' AND s.starts_at>CURRENT_TIMESTAMP AND s.starts_at=?
                """, Integer.class, candidate.appointmentId(), Timestamp.valueOf(candidate.startsAt()));
        if (active == null || active != 1) {
            jdbc.update("UPDATE ai_reminders SET status='SKIPPED', lease_until=NULL WHERE appointment_id=?", candidate.appointmentId());
            return false;
        }
        // Facts come from the database, rather than relying on the model to reproduce them accurately.
        String message = greeting.strip() + "\nAppointment " + candidate.appointmentId() + " with " + candidate.doctor()
                + " at " + candidate.startsAt() + ". Manage your appointment in the clinic app.";
        String event = outbox.enqueueMessage(candidate.recipient(), NotificationRequest.Type.APPOINTMENT_REMINDER,
                candidate.appointmentId(), message);
        jdbc.update("UPDATE ai_reminders SET status='ENQUEUED', event_id=?, queued_at=CURRENT_TIMESTAMP, lease_until=NULL WHERE appointment_id=?",
                event, candidate.appointmentId());
        return true;
    }
}
