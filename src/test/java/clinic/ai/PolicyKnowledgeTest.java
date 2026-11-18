package clinic.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PolicyKnowledgeTest {
    @Test void ingestsKnowledgeSourceOnceAndRetrievesThroughRealVectorStoreWithSourceIds() {
        AtomicInteger embeddings = new AtomicInteger();
        // Small deterministic semantic categories stand in for paid remote embeddings only in this test.
        EmbeddingModel model = new EmbeddingModel() {
            float[] vector(String text) {
                return text.toLowerCase().contains("cancellation") ? new float[]{1, 0} : new float[]{0, 1};
            }
            public int dimensions() { return 2; }
            public float[] embed(Document document) { embeddings.incrementAndGet(); return vector(document.getText()); }
            public EmbeddingResponse call(EmbeddingRequest request) {
                embeddings.addAndGet(request.getInstructions().size());
                return new EmbeddingResponse(java.util.stream.IntStream.range(0, request.getInstructions().size())
                        .mapToObj(i -> new Embedding(vector(request.getInstructions().get(i)), i)).toList());
            }
        };
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("policyVectors", SimpleVectorStore.builder(model).build());
        var policies = new PolicyKnowledge(factory.getBeanProvider(SimpleVectorStore.class));
        var first = policies.search("cancellation");
        assertFalse(first.isEmpty());
        assertTrue(first.stream().anyMatch(p -> p.source().contains("Cancellation and history")));
        assertTrue(first.stream().allMatch(p -> p.text().toLowerCase().contains("cancellation")));
        int callsAfterIngest = embeddings.get();
        policies.search("cancellation");
        assertEquals(callsAfterIngest + 1, embeddings.get(), "Second query must not re-embed the source corpus");
        assertEquals(7, PolicyKnowledge.documents().size());
    }
}
