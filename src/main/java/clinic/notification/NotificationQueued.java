package clinic.notification;

public record NotificationQueued(String eventId, NotificationRequest.Type type, long appointmentId) {}
