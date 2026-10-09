package clinic.dto;

import java.time.LocalDateTime;

public record SlotRecord(long id, long providerId, long serviceId, LocalDateTime startsAt,
        LocalDateTime endsAt, String status, long version, String providerName,
        String serviceName, boolean providerActive, boolean serviceActive) {}
