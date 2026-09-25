package clinic.dto;

import java.util.List;

public record HomeDto(
        String systemName,
        String message,
        List<ProviderDto> providers,
        List<ServiceDto> services
) {
}

