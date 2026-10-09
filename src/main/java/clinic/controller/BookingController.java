package clinic.controller;

import clinic.dto.AppointmentDto;
import clinic.dto.SlotRecord;
import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BookingController {
    private final BookingService service;
    public BookingController(BookingService service) { this.service = service; }
    public record BookRequest(@Positive long slotId, @Positive long serviceId) {}
    public record SlotRequest(@Positive long serviceId, @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {}
    @PostMapping("/appointments")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public AppointmentDto book(@AuthenticationPrincipal AccountPrincipal user, @Valid @RequestBody BookRequest request) {
        return service.book(user, request.slotId(), request.serviceId());
    }
    @GetMapping("/appointments")
    public List<AppointmentDto> myAppointments(@AuthenticationPrincipal AccountPrincipal user) { return service.myAppointments(user); }
    @PostMapping("/appointments/{id}/cancel")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long id) { service.cancel(user, id); }
    @GetMapping("/doctor/appointments")
    public List<AppointmentDto> doctorAppointments(@AuthenticationPrincipal AccountPrincipal user) { return service.doctorAppointments(user); }
    @GetMapping("/doctor/slots")
    public List<SlotRecord> doctorSlots(@AuthenticationPrincipal AccountPrincipal user) { return service.doctorSlots(user); }
    @PostMapping("/doctor/slots")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String, Long> create(@AuthenticationPrincipal AccountPrincipal user, @Valid @RequestBody SlotRequest request) {
        return Map.of("id", service.createSlot(user, request.serviceId(), request.startsAt(), request.endsAt()));
    }
    @DeleteMapping("/doctor/slots/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long id) { service.removeSlot(user, id); }
}
