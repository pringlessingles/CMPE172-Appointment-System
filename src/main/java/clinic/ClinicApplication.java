package clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;

// Embeddings share our API-key-only client; the stock connection auto-config initializes even when AI is off.
@SpringBootApplication(exclude = {GoogleGenAiEmbeddingConnectionAutoConfiguration.class, GoogleGenAiTextEmbeddingAutoConfiguration.class})
@EnableScheduling
public class ClinicApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicApplication.class, args);
    }
}
