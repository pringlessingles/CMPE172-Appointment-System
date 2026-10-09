package clinic.controller;

import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import clinic.service.ClinicService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
public class PageController {
    private final ClinicService clinic;
    private final BookingService booking;
    public PageController(ClinicService clinic, BookingService booking) { this.clinic = clinic; this.booking = booking; }
    @ModelAttribute
    public void account(@AuthenticationPrincipal AccountPrincipal user, Model model) { model.addAttribute("account", user); }
    @GetMapping("/")
    public String home(Model model) { model.addAttribute("home", clinic.getHome()); return "home"; }
    @GetMapping("/login")
    public String login() { return "login"; }
    @GetMapping("/available")
    public String available(@RequestParam(required = false) Long providerId, @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size, Model model) {
        model.addAttribute("home", clinic.getHome());
        model.addAttribute("results", clinic.getAvailableSlots(providerId, serviceId, date, page, size));
        model.addAttribute("providerId", providerId); model.addAttribute("serviceId", serviceId); model.addAttribute("date", date);
        return "available";
    }
    @GetMapping("/book/{id}")
    public String bookForm(@PathVariable long id, Model model) { model.addAttribute("slot", booking.slot(id)); return "book"; }
    @PostMapping("/book/{id}")
    public String book(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long id,
            @RequestParam long serviceId, @RequestParam(defaultValue = "false") boolean confirm) {
        if (!confirm) throw new IllegalArgumentException("Please confirm the appointment details");
        return "redirect:/confirmation/" + booking.book(user, id, serviceId).id();
    }
    @GetMapping("/confirmation/{id}")
    public String confirmation(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long id, Model model) {
        model.addAttribute("appointment", booking.ownedAppointment(user, id)); return "confirmation";
    }
    @GetMapping("/my-appointments")
    public String myAppointments(@AuthenticationPrincipal AccountPrincipal user, Model model) {
        var all = booking.myAppointments(user);
        model.addAttribute("upcoming", all.stream().filter(a -> a.status().equals("BOOKED") && a.startsAt().isAfter(LocalDateTime.now())).toList());
        model.addAttribute("history", all.stream().filter(a -> !a.status().equals("BOOKED") || !a.startsAt().isAfter(LocalDateTime.now())).toList());
        return "appointments";
    }
    @PostMapping("/my-appointments/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long id) { booking.cancel(user, id); return "redirect:/my-appointments"; }
    @GetMapping("/doctor")
    public String doctor(@AuthenticationPrincipal AccountPrincipal user, Model model) {
        long id = booking.doctorId(user);
        model.addAttribute("services", clinic.getHome().services().stream().filter(s -> s.providerId() == id).toList());
        model.addAttribute("slots", booking.doctorSlots(user)); model.addAttribute("appointments", booking.doctorAppointments(user));
        return "doctor";
    }
    @PostMapping("/doctor/slots")
    public String create(@AuthenticationPrincipal AccountPrincipal user, @RequestParam long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt) {
        booking.createSlot(user, serviceId, startsAt, endsAt); return "redirect:/doctor";
    }
    @PostMapping("/doctor/slots/{id}/remove")
    public String remove(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long id) { booking.removeSlot(user, id); return "redirect:/doctor"; }
}
