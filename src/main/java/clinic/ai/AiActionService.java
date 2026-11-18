package clinic.ai;

import clinic.dto.AppointmentDto;
import clinic.dto.SlotRecord;
import clinic.repository.BookingRepository;
import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** No model-callable method can consume a confirmation. Only the authenticated UI/API can. */
@Service
public class AiActionService {
    public record Action(String id, String kind, String summary, LocalDateTime expiresAt) { }
    private final JdbcTemplate jdbc;
    private final AiConversationStore conversations;
    private final BookingService booking;
    private final BookingRepository repository;
    public AiActionService(JdbcTemplate jdbc, AiConversationStore conversations,
            BookingService booking, BookingRepository repository) {
        this.jdbc = jdbc; this.conversations = conversations; this.booking = booking; this.repository = repository;
    }

    @Transactional
    public Action proposeBooking(AccountPrincipal actor, String conversation, long slotId, long serviceId) {
        conversations.requireOwner(actor, conversation);
        SlotRecord slot = booking.slot(slotId);
        if (slot.serviceId() != serviceId) throw new IllegalArgumentException("Service does not match slot");
        if (!"AVAILABLE".equals(slot.status()) || !slot.startsAt().isAfter(LocalDateTime.now())
                || !slot.providerActive() || !slot.serviceActive())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot unavailable. Search for alternatives.");
        return save(conversation, "BOOK", slot, null,
                "Book " + slot.serviceName() + " with " + slot.providerName() + " from " + slot.startsAt() + " to " + slot.endsAt());
    }

    @Transactional
    public Action proposeCancellation(AccountPrincipal actor, String conversation, long appointmentId) {
        conversations.requireOwner(actor, conversation);
        AppointmentDto appointment = booking.ownedAppointment(actor, appointmentId);
        if (!"BOOKED".equals(appointment.status()) || !appointment.startsAt().isAfter(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only future booked appointments can be cancelled");
        SlotRecord slot = booking.slot(appointment.availabilitySlotId());
        return save(conversation, "CANCEL", slot, appointmentId,
                "Cancel appointment " + appointmentId + ": " + slot.serviceName() + " with " + slot.providerName()
                        + " from " + slot.startsAt() + " to " + slot.endsAt());
    }
    private Action save(String conversation, String kind, SlotRecord slot, Long appointmentId, String summary) {
        conversations.lock(conversation);
        jdbc.update("UPDATE ai_actions SET status='DISCARDED' WHERE conversation_id=? AND status='PENDING'", conversation);
        String id = UUID.randomUUID().toString();
        LocalDateTime expires = LocalDateTime.now().plusMinutes(10);
        jdbc.update("""
                INSERT INTO ai_actions(id, conversation_id, kind, slot_id, service_id, appointment_id,
                                       slot_version, summary, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, conversation, kind, slot.id(), slot.serviceId(), appointmentId, slot.version(), summary, Timestamp.valueOf(expires));
        return new Action(id, kind, summary, expires);
    }
    public Optional<Action> pending(AccountPrincipal actor, String conversation) {
        conversations.requireOwner(actor, conversation);
        return jdbc.query("""
                SELECT id, kind, summary, expires_at FROM ai_actions
                WHERE conversation_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP
                """, (rs, n) -> new Action(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toLocalDateTime()), conversation).stream().findFirst();
    }

    @Transactional
    public String confirm(AccountPrincipal actor, String conversation, String actionId) {
        conversations.requireOwner(actor, conversation);
        conversations.lock(conversation);
        var rows = jdbc.queryForList("""
                SELECT * FROM ai_actions WHERE id=? AND conversation_id=? AND status='PENDING'
                AND expires_at>CURRENT_TIMESTAMP FOR UPDATE
                """, actionId, conversation);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmation expired or already used. Ask again.");
        var action = rows.getFirst();
        long slotId = ((Number) action.get("slot_id")).longValue();
        var slot = booking.slot(slotId);
        repository.lockProvider(slot.providerId());
        // The proposal's exact version must still be available, even after book/cancel/rebook cycles.
        if (booking.slot(slotId).version() != ((Number) action.get("slot_version")).longValue())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The appointment changed. Ask for a new proposal.");
        String result;
        if ("BOOK".equals(action.get("kind"))) {
            var booked = booking.book(actor, slotId, ((Number) action.get("service_id")).longValue());
            result = "Booked appointment " + booked.id() + ". " + action.get("summary");
        } else {
            long appointmentId = ((Number) action.get("appointment_id")).longValue();
            booking.cancel(actor, appointmentId);
            result = "Cancelled appointment " + appointmentId + ".";
        }
        jdbc.update("UPDATE ai_actions SET status='CONFIRMED' WHERE id=?", actionId);
        conversations.append(conversation, "USER", "Confirmed: " + action.get("summary"));
        conversations.append(conversation, "ASSISTANT", result);
        return result;
    }

    @Transactional
    public void discard(AccountPrincipal actor, String conversation) {
        conversations.requireOwner(actor, conversation);
        conversations.lock(conversation);
        jdbc.update("UPDATE ai_actions SET status='DISCARDED' WHERE conversation_id=? AND status='PENDING'", conversation);
    }
}
