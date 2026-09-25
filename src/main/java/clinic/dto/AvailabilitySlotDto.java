package clinic.dto;

import java.time.LocalDateTime;

public record AvailabilitySlotDto(
        long id,
        long providerId,
        String providerName,
        String specialty,
        long serviceId,
        String serviceName,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
}

