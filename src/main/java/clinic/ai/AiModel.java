package clinic.ai;

import org.springframework.ai.chat.messages.Message;
import java.util.List;

/** Boundary for real Gemini calls; deterministic fakes are supplied only by tests. */
public interface AiModel {
    String chat(String instructions, List<Message> history, Object tools);
    String generate(String instructions, String input);
}
