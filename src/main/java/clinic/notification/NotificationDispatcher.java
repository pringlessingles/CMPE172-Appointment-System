package clinic.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private final NotificationOutbox outbox;
    private final NotificationGateway gateway;
    private final MeterRegistry metrics;
    private final long recoveryMillis;
    private final boolean enabled;
    public NotificationDispatcher(NotificationOutbox outbox, NotificationGateway gateway, MeterRegistry metrics,
            @Value("${notifications.recovery-delay-ms:60000}") long recoveryMillis,
            @Value("${notifications.delivery-enabled:true}") boolean enabled) {
        this.outbox = outbox; this.gateway = gateway; this.metrics = metrics;
        this.recoveryMillis = Math.max(1000, recoveryMillis); this.enabled = enabled;
    }
    // AFTER_COMMIT still has bound transaction resources: suspend them before any HTTP/JDBC work.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deliver(String id) {
        if (!enabled) return;
        try {
            var pending = outbox.pending(id);
            if (pending.isEmpty()) return;
            if (gateway.send(pending.get())) {
                outbox.delivered(id);
                metrics.counter("notifications.delivered").increment();
                log.info("notification_delivered eventId={}", id);
            } else {
                outbox.defer(id, recoveryMillis);
                metrics.counter("notifications.deferred").increment();
                log.error("notification_deferred eventId={} recovery=outbox_retry", id);
            }
        } catch (Exception e) {
            // A notification failure must never escape into a committed booking's response.
            log.error("notification_dispatch_failed eventId={} reason={}", id, e.getClass().getSimpleName());
        }
    }
    @Scheduled(fixedDelayString = "${notifications.recovery-delay-ms:60000}",
            initialDelayString = "${notifications.recovery-delay-ms:60000}")
    public void recoverPending() {
        if (!enabled) return;
        try { outbox.due().forEach(this::deliver); }
        catch (Exception e) { log.error("notification_recovery_failed reason={}", e.getClass().getSimpleName()); }
    }
}
