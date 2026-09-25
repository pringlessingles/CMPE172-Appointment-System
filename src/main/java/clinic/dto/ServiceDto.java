package clinic.dto;

import java.math.BigDecimal;

public record ServiceDto(
        long id,
        long providerId,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price
) {
}

