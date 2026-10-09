package clinic;

import clinic.repository.BookingRepository;
import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingIntegrationTest {
    @Autowired BookingService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired UserDetailsService users;
    @SpyBean BookingRepository repository;
    AccountPrincipal patient() { return (AccountPrincipal) users.loadUserByUsername("sofia.reyes@example.com"); }
    AccountPrincipal other() { return (AccountPrincipal) users.loadUserByUsername("alex.morgan@example.com"); }
    AccountPrincipal doctor() { return (AccountPrincipal) users.loadUserByUsername("maya.chen@doctor.example"); }
    @BeforeEach void clean() {
        reset(repository);
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots WHERE id > 7");
        jdbc.update("UPDATE availability_slots SET status = CASE WHEN id = 7 THEN 'BLOCKED' ELSE 'AVAILABLE' END, version = 0");
        jdbc.update("UPDATE availability_slots SET starts_at = DATEADD('DAY',1,CURRENT_DATE) + TIME '08:00:00', ends_at = DATEADD('DAY',1,CURRENT_DATE) + TIME '08:30:00' WHERE id = 1");
    }
    @Test void concurrentReadersExactlyOneBookingWins() throws Exception {
        var barrier = new CyclicBarrier(2);
        var racing = new AtomicBoolean(true);
        doAnswer(call -> {
            Object result = call.callRealMethod();
            if (racing.get()) barrier.await(10, TimeUnit.SECONDS);
            return result;
        }).when(repository).slot(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> attempt(patient()));
            Future<Integer> second = pool.submit(() -> attempt(other()));
            int a = first.get(15, TimeUnit.SECONDS), b = second.get(15, TimeUnit.SECONDS);
            assertTrue((a == 201 && b == 409) || (a == 409 && b == 201));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE status = 'BOOKED' AND availability_slot_id = 1", Integer.class));
            assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM availability_slots WHERE id = 1", String.class));
        } finally { racing.set(false); pool.shutdownNow(); }
    }
    int attempt(AccountPrincipal user) {
        try { service.book(user, 1, 1); return 201; }
        catch (ResponseStatusException e) { return e.getStatusCode().value(); }
    }
    @Test void cancellationAllowsRebookingAndRetainsHistory() {
        long first = service.book(patient(), 1, 1).id();
        service.cancel(patient(), first);
        long second = service.book(other(), 1, 1).id();
        assertNotEquals(first, second);
        assertEquals("CANCELLED", repository.appointment(first).orElseThrow().status());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE availability_slot_id = 1", Integer.class));
    }
    @Test void failedInsertRollsBackSlotClaim() {
        doThrow(new DataIntegrityViolationException("simulated insert failure")).when(repository).insertAppointment(anyLong(), any());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.book(patient(), 1, 1)).getStatusCode().value());
        assertEquals("AVAILABLE", jdbc.queryForObject("SELECT status FROM availability_slots WHERE id = 1", String.class));
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM availability_slots WHERE id = 1", Long.class));
    }
    @Test void uniqueConstraintBackstopsDuplicateBooking() {
        service.book(patient(), 1, 1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO appointments(customer_user_id,provider_id,service_id,availability_slot_id,status) VALUES(5,1,1,1,'BOOKED')"));
    }
    @Test void compositeForeignKeysRejectMismatchedService() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO availability_slots(provider_id,service_id,starts_at,ends_at) VALUES(1,3,DATEADD('DAY',5,CURRENT_TIMESTAMP),DATEADD('MINUTE',30,DATEADD('DAY',5,CURRENT_TIMESTAMP)))"));
    }
    @Test void loginCreatesSessionAndAuthorizesUsingStoredRole() throws Exception {
        var result = mvc.perform(formLogin().user("sofia.reyes@example.com").password("Appointment123!"))
                .andExpect(authenticated().withRoles("CUSTOMER")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/my-appointments").session(session)).andExpect(status().isOk());
        mvc.perform(get("/doctor").session(session)).andExpect(status().isForbidden());
        mvc.perform(formLogin().user("sofia.reyes@example.com").password("wrong")).andExpect(unauthenticated());
    }
    @Test void wrongRoleMissingCsrfAndNonOwnerAreRejected() throws Exception {
        mvc.perform(post("/api/appointments").with(user(doctor())).with(csrf()).contentType("application/json")
                .content("{\"slotId\":1,\"serviceId\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments").with(user(patient())).contentType("application/json")
                .content("{\"slotId\":1,\"serviceId\":1}")).andExpect(status().isForbidden());
        long id = service.book(patient(), 1, 1).id();
        mvc.perform(post("/api/appointments/" + id + "/cancel").with(user(other())).with(csrf()))
                .andExpect(status().isForbidden());
        assertEquals("BOOKED", repository.appointment(id).orElseThrow().status());
    }
    @Test void browserFlowAndDoctorPagesRender() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Meet the doctors")));
        mvc.perform(get("/available")).andExpect(status().isOk());
        mvc.perform(get("/book/1").with(user(patient()))).andExpect(status().isOk());
        var booked = mvc.perform(post("/book/1").with(user(patient())).with(csrf())
                .param("serviceId","1").param("confirm","true")).andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get(booked.getResponse().getRedirectedUrl()).with(user(patient()))).andExpect(status().isOk());
        mvc.perform(get("/my-appointments").with(user(patient()))).andExpect(status().isOk());
        mvc.perform(get("/doctor").with(user(doctor()))).andExpect(status().isOk());
    }
    @Test void providerCannotRemoveBookedSlotOrOtherDoctorsSlot() {
        service.book(patient(), 1, 1);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.removeSlot(doctor(), 1)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.removeSlot(doctor(), 3)).getStatusCode().value());
    }
    @Test void providerCanCreateThenRemoveOwnAvailability() {
        var start = java.time.LocalDateTime.now().plusDays(8);
        long id = service.createSlot(doctor(), 1, start, start.plusMinutes(30));
        service.removeSlot(doctor(), id);
        assertEquals("BLOCKED", repository.slot(id).orElseThrow().status());
    }
    @Test void pastAppointmentsBecomeCompletedAndCannotBeCancelled() {
        long id = service.book(patient(), 1, 1).id();
        var end = java.time.LocalDateTime.now().minusHours(1);
        jdbc.update("UPDATE availability_slots SET starts_at = ?, ends_at = ? WHERE id = 1", end.minusMinutes(30), end);
        assertEquals("COMPLETED", service.myAppointments(patient()).get(0).status());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.cancel(patient(), id)).getStatusCode().value());
        repository.completePastAppointments();
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, id));
        assertEquals("BLOCKED", repository.slot(1).orElseThrow().status());
    }
    @Test void apiBooksListsAndCancelsUsingAuthenticatedPatient() throws Exception {
        mvc.perform(post("/api/appointments").with(user(patient())).with(csrf()).contentType("application/json")
                .content("{\"slotId\":1,\"serviceId\":1}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerUserId").value(3)).andExpect(jsonPath("$.status").value("BOOKED"));
        long id = service.myAppointments(patient()).get(0).id();
        mvc.perform(get("/api/appointments").with(user(other()))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/appointments/" + id + "/cancel").with(user(patient())).with(csrf())).andExpect(status().isNoContent());
        assertEquals("AVAILABLE", repository.slot(1).orElseThrow().status());
    }
    @Test void invalidFiltersRenderReadableErrorPage() throws Exception {
        mvc.perform(get("/available").param("page", "-1")).andExpect(status().isBadRequest())
                .andExpect(view().name("error"));
    }
}
