package clinic.dto;

import java.time.LocalDateTime;

public record AppointmentDto(long id, long customerUserId, long providerId, long serviceId,
        long availabilitySlotId, String patientName, String providerName, String serviceName,
        LocalDateTime startsAt, LocalDateTime endsAt, String status) {}
