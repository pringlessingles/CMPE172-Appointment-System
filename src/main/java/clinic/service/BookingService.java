package clinic.service;

import clinic.dto.AppointmentDto;
import clinic.dto.SlotRecord;
import clinic.repository.BookingRepository;
import clinic.security.AccountPrincipal;
import clinic.notification.NotificationOutbox;
import clinic.notification.NotificationRequest.Type;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingService {
    private final BookingRepository repository;
    private final NotificationOutbox notifications;
    public BookingService(BookingRepository repository, NotificationOutbox notifications) {
        this.repository = repository; this.notifications = notifications;
    }
    private void role(AccountPrincipal actor, String role) {
        if (actor == null || !role.equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This action is not allowed");
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    public SlotRecord slot(long id) {
        return repository.slot(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
    }
    public long doctorId(AccountPrincipal actor) {
        role(actor, "PROVIDER");
        return repository.providerForUser(actor.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active doctor profile required"));
    }
    public AppointmentDto ownedAppointment(AccountPrincipal actor, long id) {
        role(actor, "CUSTOMER");
        AppointmentDto a = repository.appointment(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (a.customerUserId() != actor.id()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This appointment belongs to another patient");
        return a;
    }
    public List<AppointmentDto> myAppointments(AccountPrincipal actor) {
        role(actor, "CUSTOMER");
        return repository.customerAppointments(actor.id());
    }
    public List<SlotRecord> doctorSlots(AccountPrincipal actor) { return repository.providerSlots(doctorId(actor)); }
    public List<AppointmentDto> doctorAppointments(AccountPrincipal actor) { return repository.providerAppointments(doctorId(actor)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDto book(AccountPrincipal actor, long slotId, long serviceId) {
        role(actor, "CUSTOMER");
        SlotRecord slot = slot(slotId);
        if (slot.serviceId() != serviceId) throw new IllegalArgumentException("Service does not match the selected slot");
        if (!slot.providerActive() || !slot.serviceActive()) throw conflict("Doctor or service is unavailable");
        if (!slot.startsAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("Cannot book a past appointment");
        if (!"AVAILABLE".equals(slot.status())) throw conflict("This slot has already been booked or removed");
        repository.lockProvider(slot.providerId());
        if (repository.overlappingBooking(slot)) throw conflict("This doctor already has an overlapping appointment");
        // The version predicate makes the update an atomic compare-and-set.
        // A stale reader loses even if a slot was booked then cancelled in between.
        if (repository.claim(slot.id(), slot.version()) != 1) throw conflict("Someone else changed this slot. Choose another appointment");
        try {
            long id = repository.insertAppointment(actor.id(), slot);
            notifications.enqueue(actor.getUsername(), Type.BOOKING_CONFIRMED, id);
            return repository.appointment(id).orElseThrow();
        } catch (DataIntegrityViolationException e) {
            // Throwing a runtime exception rolls back the slot claim as well.
            throw conflict("This slot could not be booked");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(AccountPrincipal actor, long id) {
        AppointmentDto appointment = ownedAppointment(actor, id);
        repository.lockProvider(appointment.providerId());
        if (!"BOOKED".equals(appointment.status())) throw conflict("Only booked appointments can be cancelled");
        if (!appointment.startsAt().isAfter(LocalDateTime.now())) throw conflict("Cannot cancel an appointment that has started");
        if (repository.cancel(id, actor.id()) != 1) throw conflict("Appointment was already changed");
        if (repository.release(appointment.availabilitySlotId()) != 1) throw conflict("Slot was already changed");
        notifications.enqueue(actor.getUsername(), Type.BOOKING_CANCELLED, id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long createSlot(AccountPrincipal actor, long serviceId, LocalDateTime start, LocalDateTime end) {
        long doctorId = doctorId(actor);
        if (start == null || end == null || !end.isAfter(start) || !start.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("Choose a future start time and an end time after it");
        if (!repository.serviceBelongsTo(serviceId, doctorId)) throw new IllegalArgumentException("Choose one of your active services");
        repository.lockProvider(doctorId);
        if (repository.overlappingSlot(doctorId, start, end))
            throw conflict("This time overlaps another session for this doctor");
        try { return repository.createSlot(doctorId, serviceId, start, end); }
        catch (DataIntegrityViolationException e) { throw conflict("This time conflicts with the doctor's schedule"); }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeSlot(AccountPrincipal actor, long id) {
        long doctorId = doctorId(actor);
        repository.lockProvider(doctorId);
        SlotRecord slot = slot(id);
        if (slot.providerId() != doctorId) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This slot belongs to another doctor");
        if (repository.block(id, doctorId, slot.version()) != 1) throw conflict("Only available, unchanged slots can be removed");
    }
}
