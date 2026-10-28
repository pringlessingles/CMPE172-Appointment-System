package clinic.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// One coarse-grained request contains everything the receiver needs.
public record NotificationRequest(@NotNull UUID eventId,
        @NotBlank @Email @Size(max = 255) String recipient,
        @NotNull Type type, @NotBlank @Size(max = 1000) String message) {
    public enum Type { BOOKING_CONFIRMED, BOOKING_CANCELLED }
}
