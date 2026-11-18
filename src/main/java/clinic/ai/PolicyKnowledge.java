package clinic.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PolicyKnowledge {
    public record Passage(String source, String text) { }
    private final ObjectProvider<SimpleVectorStore> stores;
    private boolean ingested;
    public PolicyKnowledge(ObjectProvider<SimpleVectorStore> stores) { this.stores = stores; }

    // Lazy ingestion keeps unavailable Gemini/embedding APIs from breaking ordinary startup or health checks.
    public synchronized List<Passage> search(String question) {
        SimpleVectorStore store = stores.getIfAvailable();
        if (store == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI policy search is disabled");
        if (!ingested) {
            store.add(documents());
            ingested = true;
        }
        return store.similaritySearch(SearchRequest.builder().query(question).topK(3).similarityThreshold(0.45).build())
                .stream().map(d -> new Passage(String.valueOf(d.getMetadata().get("source")), d.getText())).toList();
    }

    static List<Document> documents() {
        try {
            String text = new ClassPathResource("ai/clinic-policies.md").getContentAsString(StandardCharsets.UTF_8);
            var documents = new ArrayList<Document>();
            for (String part : text.split("(?m)^## ")) {
                if (part.isBlank() || part.startsWith("# ")) continue;
                String heading = part.lines().findFirst().orElseThrow();
                documents.add(new Document("clinic-policy-" + documents.size(), part.strip(),
                        Map.of("source", "clinic-policies.md#" + heading, "title", heading)));
            }
            return documents;
        } catch (IOException e) { throw new IllegalStateException("Cannot read clinic policy knowledge source", e); }
    }
}
