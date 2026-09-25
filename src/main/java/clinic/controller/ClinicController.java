package clinic.controller;

import clinic.dto.HomeDto;
import clinic.dto.SlotPageDto;
import clinic.service.ClinicService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class ClinicController {

    private final ClinicService service;

    public ClinicController(ClinicService service) {
        this.service = service;
    }

    @GetMapping("/")
    public HomeDto home() {
        return service.getHome();
    }

    @GetMapping("/slots")
    public SlotPageDto availableSlots(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.getAvailableSlots(providerId, serviceId, date, page, size);
    }
}

