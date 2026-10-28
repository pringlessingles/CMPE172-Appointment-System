package clinic.notification;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationOutbox {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    public NotificationOutbox(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc; this.events = events;
    }
    // Called inside the booking/cancellation transaction: rollback removes this row too.
    public void enqueue(String recipient, NotificationRequest.Type type, long appointmentId) {
        String id = UUID.randomUUID().toString();
        String message = "Appointment " + appointmentId +
                (type == NotificationRequest.Type.BOOKING_CONFIRMED ? " is confirmed." : " is cancelled.");
        jdbc.update("INSERT INTO notification_outbox(event_id, recipient, type, message) VALUES (?, ?, ?, ?)",
                id, recipient, type.name(), message);
        events.publishEvent(new NotificationQueued(id, type, appointmentId));
    }
    public Optional<NotificationRequest> pending(String id) {
        return jdbc.query("SELECT * FROM notification_outbox WHERE event_id = ? AND delivered_at IS NULL",
                (rs, n) -> new NotificationRequest(UUID.fromString(rs.getString("event_id")),
                        rs.getString("recipient"), NotificationRequest.Type.valueOf(rs.getString("type")),
                        rs.getString("message")), id).stream().findFirst();
    }
    public List<String> due() {
        return jdbc.query("SELECT event_id FROM notification_outbox WHERE delivered_at IS NULL " +
                        "AND next_attempt_at <= CURRENT_TIMESTAMP ORDER BY created_at LIMIT 20",
                (rs, n) -> rs.getString(1));
    }
    public void delivered(String id) {
        jdbc.update("UPDATE notification_outbox SET delivered_at = CURRENT_TIMESTAMP, attempts = attempts + 1 " +
                "WHERE event_id = ? AND delivered_at IS NULL", id);
    }
    public void defer(String id, long delayMillis) {
        jdbc.update("UPDATE notification_outbox SET attempts = attempts + 1, next_attempt_at = ? " +
                "WHERE event_id = ? AND delivered_at IS NULL", Timestamp.from(Instant.now().plusMillis(delayMillis)), id);
    }
}
