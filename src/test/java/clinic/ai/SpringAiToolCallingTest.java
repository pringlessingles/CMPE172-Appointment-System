package clinic.ai;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.retry.support.RetryTemplate;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SpringAiToolCallingTest {
    public static class DemoTools {
        final AtomicInteger called = new AtomicInteger();
        @Tool(description = "Prepare a booking proposal without committing it")
        public String bookAppointment(long slotId, long serviceId) {
            if (called.incrementAndGet() > 12) throw new IllegalStateException("Tool call limit reached");
            assertEquals(1, slotId); assertEquals(1, serviceId);
            return "Proposal prepared. Patient must press Confirm.";
        }
    }
    @Test void actualGeminiAdapterAndChatClientExecuteToolAndSendToolResultBack() throws Exception {
        runLoop(false);
    }
    @Test void toolErrorsStopTheActualModelLoopRatherThanReturningErrorTextAndContinuing() throws Exception {
        runLoop(true);
    }
    private void runLoop(boolean repeated) throws Exception {
        var requests = new ArrayList<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String part = requests.size() == 1 || repeated
                    ? "\"functionCall\":{\"name\":\"bookAppointment\",\"args\":{\"slotId\":1,\"serviceId\":1}}"
                    : "\"text\":\"Please review the proposal and press Confirm.\"";
            byte[] response = ("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{" + part
                    + "}]},\"finishReason\":\"STOP\",\"index\":0}],\"modelVersion\":\"offline-test-model\",\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":10,\"totalTokenCount\":20}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try (Client client = Client.builder().apiKey("offline-test-placeholder").vertexAI(false)
                .httpOptions(HttpOptions.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .timeout(2000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build()) {
            var chatModel = GoogleGenAiChatModel.builder().genAiClient(client)
                    .toolCallingManager(ToolCallingManager.builder().toolExecutionExceptionProcessor(
                            new AiConfiguration().toolExecutionExceptionProcessor()).build())
                    .defaultOptions(GoogleGenAiChatOptions.builder().model("offline-test-model").build())
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
            var ai = new AiConfiguration().geminiAiModel(chatModel);
            var tools = new DemoTools();
            if (repeated) {
                assertThrows(RuntimeException.class, () -> ai.chat("Use a tool", List.of(new UserMessage("Book")), tools));
                assertEquals(13, requests.size());
                assertEquals(13, tools.called.get());
                return;
            }
            String answer = ai.chat("Use a tool to prepare the booking.", List.of(new UserMessage("Book slot 1, service 1")), tools);
            assertTrue(answer.contains("Confirm"));
            assertEquals(1, tools.called.get());
            assertEquals(2, requests.size());
            assertTrue(requests.get(1).contains("functionResponse"));
            assertTrue(requests.get(1).contains("Proposal prepared"));
        } finally { server.stop(0); }
    }
}
