package clinic;

import clinic.dto.AppointmentDto;
import clinic.dto.SlotRecord;
import clinic.repository.BookingRepository;
import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import clinic.notification.NotificationOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    BookingRepository repo;
    BookingService service;
    AccountPrincipal patient = new AccountPrincipal(3, "patient@example.com", "unused", "CUSTOMER");
    AccountPrincipal doctor = new AccountPrincipal(1, "doctor@example.com", "unused", "PROVIDER");
    LocalDateTime start = LocalDateTime.now().plusDays(2);
    @BeforeEach void setup() { repo = mock(BookingRepository.class); service = new BookingService(repo, mock(NotificationOutbox.class)); }
    SlotRecord slot(String status) { return new SlotRecord(1, 1, 1, start, start.plusMinutes(30), status, 0, "Doctor", "Consultation", true, true); }
    @Test void mismatchedServiceNeverClaimsSlot() {
        when(repo.slot(1)).thenReturn(Optional.of(slot("AVAILABLE")));
        assertThrows(IllegalArgumentException.class, () -> service.book(patient, 1, 2));
        verify(repo, never()).claim(anyLong(), anyLong());
    }
    @Test void occupiedSlotReturnsConflict() {
        when(repo.slot(1)).thenReturn(Optional.of(slot("BOOKED")));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.book(patient, 1, 1)).getStatusCode().value());
    }
    @Test void staleVersionCannotInsertAppointment() {
        when(repo.slot(1)).thenReturn(Optional.of(slot("AVAILABLE")));
        when(repo.claim(1, 0)).thenReturn(0);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.book(patient, 1, 1)).getStatusCode().value());
        verify(repo, never()).insertAppointment(anyLong(), any());
    }
    @Test void overlappingBookingIsRejectedBeforeClaim() {
        SlotRecord selected = slot("AVAILABLE");
        when(repo.slot(1)).thenReturn(Optional.of(selected));
        when(repo.overlappingBooking(selected)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.book(patient, 1, 1)).getStatusCode().value());
        verify(repo).lockProvider(1);
        verify(repo, never()).claim(anyLong(), anyLong());
    }
    @Test void onlyOwnerCanCancel() {
        when(repo.appointment(9)).thenReturn(Optional.of(new AppointmentDto(9, 5, 1, 1, 1, "Other patient", "Doctor", "Consultation", start, start.plusMinutes(30), "BOOKED")));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.cancel(patient, 9)).getStatusCode().value());
        verify(repo, never()).cancel(anyLong(), anyLong());
    }
    @Test void doctorCannotBookAsPatient() {
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.book(doctor, 1, 1)).getStatusCode().value());
        verifyNoInteractions(repo);
    }
    @Test void invalidTimesCannotCreateSlot() {
        when(repo.providerForUser(1)).thenReturn(Optional.of(1L));
        assertThrows(IllegalArgumentException.class, () -> service.createSlot(doctor, 1, start, start.minusMinutes(5)));
        verify(repo, never()).createSlot(anyLong(), anyLong(), any(), any());
    }
    @Test void doctorCannotRemoveAnotherDoctorsSlot() {
        when(repo.providerForUser(1)).thenReturn(Optional.of(2L));
        when(repo.slot(1)).thenReturn(Optional.of(slot("AVAILABLE")));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.removeSlot(doctor, 1)).getStatusCode().value());
        verify(repo, never()).block(anyLong(), anyLong(), anyLong());
    }
}
