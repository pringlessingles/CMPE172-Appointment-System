package clinic.dto;

import java.util.List;

public record SlotPageDto(
        List<AvailabilitySlotDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}

