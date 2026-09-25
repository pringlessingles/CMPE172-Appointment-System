package clinic.dto;

import java.time.Instant;

public record ErrorDto(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}

