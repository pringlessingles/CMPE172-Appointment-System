package clinic.ai;

import clinic.security.AccountPrincipal;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Repository
public class AiConversationStore {
    private final JdbcTemplate jdbc;
    public AiConversationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String open(AccountPrincipal actor, String id) {
        requirePatient(actor);
        if (id != null) { requireOwner(actor, id); return id; }
        String created = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ai_conversations(id, user_id) VALUES (?, ?)", created, actor.id());
        return created;
    }
    public static void requirePatient(AccountPrincipal actor) {
        if (actor == null || !"CUSTOMER".equals(actor.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient account required");
    }
    public void requireOwner(AccountPrincipal actor, String id) {
        requirePatient(actor);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations WHERE id=? AND user_id=?",
                Integer.class, id, actor.id()) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
    }
    public void lock(String id) {
        jdbc.queryForObject("SELECT id FROM ai_conversations WHERE id=? FOR UPDATE", String.class, id);
    }
    public void append(String id, String role, String content) {
        jdbc.update("INSERT INTO ai_messages(conversation_id, role, content) VALUES (?, ?, ?)", id, role, content);
    }
    public record Turn(long id, String role, String content) { }
    public List<Turn> history(String id) {
        return jdbc.query("SELECT id, role, content FROM ai_messages WHERE conversation_id=? ORDER BY id DESC LIMIT 20",
                (rs, n) -> new Turn(rs.getLong(1), rs.getString(2), rs.getString(3)), id)
                .stream().sorted(Comparator.comparingLong(Turn::id)).toList();
    }
    public List<Message> modelHistory(String id) {
        return history(id).stream().map(t -> (Message) ("USER".equals(t.role())
                ? new UserMessage(t.content()) : new AssistantMessage(t.content()))).toList();
    }
}
