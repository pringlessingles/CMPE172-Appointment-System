package clinic;

import clinic.ai.AiModel;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/** Construct the real Gemini beans without sending any requests or requiring a real key. */
@SpringBootTest(properties = {"GEMINI_API_KEY=offline-test-placeholder", "clinic.ai.reminders.initial-delay-ms=3600000"})
@ActiveProfiles("ai")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiProfileTest {
    @Autowired ApplicationContext context;
    @Test void apiKeyOnlyProfileStartsWithRealChatAndEmbeddingModels() {
        assertEquals(1, context.getBeansOfType(Client.class).size());
        assertEquals(1, context.getBeansOfType(ChatModel.class).size());
        assertEquals(1, context.getBeansOfType(EmbeddingModel.class).size());
        assertNotNull(context.getBean(SimpleVectorStore.class));
        assertNotNull(context.getBean(AiModel.class));
        assertNull(context.getEnvironment().getProperty("spring.ai.google.genai.project-id"));
        assertNull(context.getEnvironment().getProperty("spring.ai.google.genai.location"));
    }
}
