package clinic;

import clinic.repository.BookingRepository;
import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DoctorScheduleIntegrationTest {
    @Autowired BookingService service;
    @Autowired BookingRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserDetailsService users;
    final LocalDateTime start = LocalDateTime.now().plusDays(20).withNano(0);
    AccountPrincipal account(String email) { return (AccountPrincipal) users.loadUserByUsername(email); }
    AccountPrincipal doctor() { return account("maya.chen@doctor.example"); }
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM notification_outbox"); jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots WHERE id > 7");
    }
    @Test void rejectsPartialContainedAndContainingOverlapsAcrossServices() {
        service.createSlot(doctor(), 1, start, start.plusMinutes(30));
        for (var range : new LocalDateTime[][] {
                {start.plusMinutes(15), start.plusMinutes(45)},
                {start.plusMinutes(5), start.plusMinutes(20)},
                {start.minusMinutes(15), start.plusMinutes(45)},
                {start.minusMinutes(15), start.plusMinutes(15)}}) {
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> service.createSlot(doctor(), 2, range[0], range[1])).getStatusCode().value());
        }
    }
    @Test void permitsAdjacentSlotsAndDifferentDoctorsAtTheSameTime() {
        service.createSlot(doctor(), 1, start, start.plusMinutes(30));
        assertDoesNotThrow(() -> service.createSlot(doctor(), 2, start.plusMinutes(30), start.plusMinutes(60)));
        assertDoesNotThrow(() -> service.createSlot(doctor(), 1, start.minusMinutes(30), start));
        assertDoesNotThrow(() -> service.createSlot(account("jordan.lee@doctor.example"), 3, start, start.plusMinutes(30)));
    }
    @Test void removedSlotDoesNotReserveAnOverlappingTime() {
        long id = service.createSlot(doctor(), 1, start, start.plusMinutes(30));
        service.removeSlot(doctor(), id);
        assertDoesNotThrow(() -> service.createSlot(doctor(), 2, start.plusMinutes(10), start.plusMinutes(40)));
    }
    @Test void bookedSlotPreventsNewOverlappingAvailability() {
        long id = service.createSlot(doctor(), 1, start, start.plusMinutes(30));
        service.book(account("sofia.reyes@example.com"), id, 1);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.createSlot(doctor(), 2, start.plusMinutes(10), start.plusMinutes(40))).getStatusCode().value());
    }
    @Test void rejectsBookingOverlappingLegacySlots() {
        // H2 fixture represents old data imported before the PostgreSQL exclusion constraint.
        long first = repository.createSlot(1, 1, start, start.plusMinutes(30));
        long second = repository.createSlot(1, 2, start.plusMinutes(15), start.plusMinutes(45));
        service.book(account("sofia.reyes@example.com"), first, 1);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.book(account("alex.morgan@example.com"), second, 2)).getStatusCode().value());
        assertEquals("AVAILABLE", repository.slot(second).orElseThrow().status());
    }
    @Test void simultaneousOverlappingSlotCreationOnlyOneWins() throws Exception {
        var gate = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(); return create(start); });
            var b = pool.submit(() -> { gate.await(); return create(start.plusMinutes(15)); });
            int first = a.get(10, TimeUnit.SECONDS), second = b.get(10, TimeUnit.SECONDS);
            assertTrue((first == 201 && second == 409) || (first == 409 && second == 201));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM availability_slots WHERE id > 7", Integer.class));
        }
    }
    int create(LocalDateTime time) {
        try { service.createSlot(doctor(), 1, time, time.plusMinutes(30)); return 201; }
        catch (ResponseStatusException e) { return e.getStatusCode().value(); }
    }
}
