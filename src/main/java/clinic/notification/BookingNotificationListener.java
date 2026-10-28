package clinic.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(BookingNotificationListener.class);
    private final NotificationDispatcher dispatcher;
    private final MeterRegistry metrics;
    public BookingNotificationListener(NotificationDispatcher dispatcher, MeterRegistry metrics) {
        this.dispatcher = dispatcher; this.metrics = metrics;
        metrics.counter("appointments.booked"); metrics.counter("appointments.cancelled");
        metrics.counter("notifications.delivered"); metrics.counter("notifications.deferred");
    }
    @TransactionalEventListener
    public void afterCommit(NotificationQueued event) {
        boolean booked = event.type() == NotificationRequest.Type.BOOKING_CONFIRMED;
        metrics.counter(booked ? "appointments.booked" : "appointments.cancelled").increment();
        log.info("appointment_committed appointmentId={} action={} eventId={}",
                event.appointmentId(), event.type(), event.eventId());
        dispatcher.deliver(event.eventId());
    }
}
