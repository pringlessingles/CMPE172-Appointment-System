package clinic.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.retry.support.RetryTemplate;
import java.util.List;

@Configuration
public class AiConfiguration {
    @Bean
    ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        // Do not feed unexpected exceptions (including the per-turn budget) back into a model loop.
        // Expected conflicts/validation errors are already returned as safe Result objects by the tools.
        return new DefaultToolExecutionExceptionProcessor(true);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "clinic.ai.enabled", havingValue = "true")
    Client geminiClient(@Value("${spring.ai.google.genai.api-key}") String key) {
        if (key.isBlank()) throw new IllegalArgumentException("Set GEMINI_API_KEY before enabling the ai profile");
        return Client.builder().apiKey(key).vertexAI(false).httpOptions(HttpOptions.builder()
                .timeout(30000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "clinic.ai.enabled", havingValue = "true")
    EmbeddingModel policyEmbeddingModel(Client client, @Value("${spring.ai.google.genai.api-key}") String key,
            @Value("${spring.ai.google.genai.embedding.text.options.model:gemini-embedding-001}") String model) {
        var connection = GoogleGenAiEmbeddingConnectionDetails.builder().apiKey(key).genAiClient(client).build();
        var options = GoogleGenAiTextEmbeddingOptions.builder().model(model)
                .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.SEMANTIC_SIMILARITY).build();
        return new GoogleGenAiTextEmbeddingModel(connection, options,
                RetryTemplate.builder().maxAttempts(2).fixedBackoff(500).build());
    }

    @Bean
    @ConditionalOnProperty(name = "clinic.ai.enabled", havingValue = "true")
    AiModel geminiAiModel(ChatModel model) {
        ChatClient client = ChatClient.create(model);
        return new AiModel() {
            public String chat(String instructions, List<Message> history, Object tools) {
                return client.prompt().system(instructions).messages(history).tools(tools).call().content();
            }
            public String generate(String instructions, String input) {
                return client.prompt().system(instructions).user(input).call().content();
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "clinic.ai.enabled", havingValue = "false", matchIfMissing = true)
    AiModel unavailableAiModel() {
        return new AiModel() {
            private ResponseStatusException unavailable() {
                return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AI is disabled. Configure GEMINI_API_KEY and enable the ai profile.");
            }
            public String chat(String instructions, List<Message> history, Object tools) { throw unavailable(); }
            public String generate(String instructions, String input) { throw unavailable(); }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "clinic.ai.enabled", havingValue = "true")
    SimpleVectorStore policyVectorStore(EmbeddingModel model) {
        return SimpleVectorStore.builder(model).build();
    }
}
