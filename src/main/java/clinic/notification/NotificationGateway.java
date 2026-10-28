package clinic.notification;

public interface NotificationGateway {
    // Returns false after bounded retries; callers retain the event for later recovery.
    boolean send(NotificationRequest request);
}
