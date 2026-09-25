package clinic.service;

import clinic.dto.AvailabilitySlotDto;
import clinic.dto.HomeDto;
import clinic.dto.SlotPageDto;
import clinic.repository.ClinicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ClinicService {

    private final ClinicRepository repository;

    public ClinicService(ClinicRepository repository) {
        this.repository = repository;
    }

    public HomeDto getHome() {
        return new HomeDto(
                "Community Health Clinic Appointment System",
                "Find an available primary-care or wellness appointment.",
                repository.findActiveProviders(),
                repository.findActiveServices()
        );
    }

    public SlotPageDto getAvailableSlots(
            Long providerId,
            Long serviceId,
            LocalDate date,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        long offset = (long) page * size;
        List<AvailabilitySlotDto> items = repository.findAvailableSlots(
                providerId, serviceId, date, size, offset
        );
        long totalItems = repository.countAvailableSlots(providerId, serviceId, date);
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        return new SlotPageDto(items, page, size, totalItems, totalPages);
    }
}

