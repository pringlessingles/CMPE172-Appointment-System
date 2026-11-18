package clinic.ai;

import clinic.security.AccountPrincipal;
import clinic.service.BookingService;
import clinic.service.ClinicService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** One instance per turn, with a server-bound principal and conversation. No user ID is model supplied. */
public class AppointmentTools {
    private final AccountPrincipal actor;
    private final String conversation;
    private final ClinicService clinic;
    private final BookingService booking;
    private final AiActionService actions;
    private final PolicyKnowledge policies;
    private final AtomicInteger calls = new AtomicInteger();
    public AppointmentTools(AccountPrincipal actor, String conversation, ClinicService clinic,
            BookingService booking, AiActionService actions, PolicyKnowledge policies) {
        AiConversationStore.requirePatient(actor);
        this.actor = actor; this.conversation = conversation; this.clinic = clinic;
        this.booking = booking; this.actions = actions; this.policies = policies;
    }
    public record Result(boolean success, Object data) { }
    private Result invoke(Supplier<Object> operation) {
        if (calls.incrementAndGet() > 12) throw new IllegalStateException("Tool call limit reached for this turn");
        try { return new Result(true, operation.get()); }
        catch (ResponseStatusException e) { return new Result(false, e.getReason()); }
        catch (IllegalArgumentException e) { return new Result(false, e.getMessage()); }
    }

    @Tool(description = "List current doctors, services, prices and service IDs. Use before searching when names are given.")
    public Result catalog() { return invoke(clinic::getHome); }

    @Tool(description = "Search future available slots ordered earliest first. Optional doctor, service and ISO date filters. Page starts at zero; each page has 20 slots. Search multiple dates/pages for a requested week.")
    public Result checkAvailability(@ToolParam(required = false) Long providerId,
            @ToolParam(required = false) Long serviceId, @ToolParam(required = false) String date, int page) {
        return invoke(() -> clinic.getAvailableSlots(providerId, serviceId,
                date == null || date.isBlank() ? null : LocalDate.parse(date), page, 20));
    }

    @Tool(description = "List only the logged-in patient's appointments, including IDs, dates and statuses.")
    public Result listMyAppointments() { return invoke(() -> booking.myAppointments(actor)); }

    @Tool(description = "Prepare booking of a selected available slot and matching service. Returns a server-generated proposal, never commits. Tell the patient to review the proposal and press Confirm. Do not claim it is already booked.")
    public Result bookAppointment(long slotId, long serviceId) {
        return invoke(() -> actions.proposeBooking(actor, conversation, slotId, serviceId));
    }

    @Tool(description = "Prepare cancellation of the logged-in patient's appointment. Never cancels immediately. The patient must review the server proposal and press Confirm.")
    public Result cancelAppointment(long appointmentId) {
        return invoke(() -> actions.proposeCancellation(actor, conversation, appointmentId));
    }

    @Tool(description = "Retrieve clinic policy/FAQ passages with source IDs using semantic vector search. Ground policy answers only in these passages and cite their source IDs. If none match, say the policy is unknown.")
    public Result clinicPolicy(String question) { return invoke(() -> policies.search(question)); }
}
