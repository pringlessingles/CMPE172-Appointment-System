package clinic.ai;

import clinic.security.AccountPrincipal;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    public record ChatRequest(@Size(max = 36) String conversationId, @NotBlank @Size(max = 4000) String message) { }
    public record Question(@NotBlank @Size(max = 2000) String question) { }
    public record Confirmation(@NotBlank @Size(max = 36) String confirmationId) { }
    public record History(List<AiConversationStore.Turn> messages, AiActionService.Action pendingAction) { }
    private final AiAssistant assistant;
    private final AiConversationStore conversations;
    private final AiActionService actions;
    private final ReminderAgent reminders;
    public AiController(AiAssistant assistant, AiConversationStore conversations, AiActionService actions, ReminderAgent reminders) {
        this.assistant = assistant; this.conversations = conversations; this.actions = actions; this.reminders = reminders;
    }

    @PostMapping("/chat")
    public AiAssistant.Reply chat(@AuthenticationPrincipal AccountPrincipal actor, @Valid @RequestBody ChatRequest request,
            HttpSession session) {
        // Serialize double submissions within one login session, including confirmation requests below.
        synchronized (WebUtils.getSessionMutex(session)) {
            return assistant.chat(actor, request.conversationId(), request.message());
        }
    }
    @GetMapping("/conversations/{id}")
    public History history(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable String id) {
        conversations.requireOwner(actor, id);
        return new History(conversations.history(id), actions.pending(actor, id).orElse(null));
    }
    @PostMapping("/conversations/{id}/confirm")
    public AiAssistant.Reply confirm(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable String id,
            @Valid @RequestBody Confirmation request, HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            return new AiAssistant.Reply(id, actions.confirm(actor, id, request.confirmationId()), null);
        }
    }
    @PostMapping("/conversations/{id}/discard")
    public void discard(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable String id, HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) { actions.discard(actor, id); }
    }
    @PostMapping("/questions")
    public AiAssistant.PolicyAnswer question(@Valid @RequestBody Question question) { return assistant.question(question.question()); }

    @PostMapping("/reminders/run")
    public ReminderAgent.RunResult runReminders() { return reminders.run(); }
}
