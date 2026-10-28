package clinic.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpNotificationGateway implements NotificationGateway {
    private static final Logger log = LoggerFactory.getLogger(HttpNotificationGateway.class);
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String token;
    private final int maxAttempts;
    private final long timeoutMillis, backoffMillis;

    public HttpNotificationGateway(ObjectMapper mapper,
            @Value("${notifications.url}") String endpoint,
            @Value("${notifications.token}") String token,
            @Value("${notifications.max-attempts:3}") int maxAttempts,
            @Value("${notifications.timeout-ms:750}") long timeoutMillis,
            @Value("${notifications.backoff-ms:100}") long backoffMillis) {
        if (maxAttempts < 1 || maxAttempts > 5 || timeoutMillis < 1 || timeoutMillis > 10000 ||
                backoffMillis < 0 || backoffMillis > 2000 || token.isBlank())
            throw new IllegalArgumentException("Invalid notification timeout, retry, or token configuration");
        this.mapper = mapper; this.endpoint = URI.create(endpoint); this.token = token;
        this.maxAttempts = maxAttempts; this.timeoutMillis = timeoutMillis; this.backoffMillis = backoffMillis;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override
    public boolean send(NotificationRequest request) {
        final String body;
        try { body = mapper.writeValueAsString(request); }
        catch (Exception e) { log.error("notification_serialization_failed eventId={}", request.eventId()); return false; }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest call = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMillis(timeoutMillis))
                        .header("Content-Type", "application/json").header("X-Notification-Token", token)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                int status = client.send(call, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status >= 200 && status < 300) return true;
                log.warn("notification_http_failure eventId={} attempt={} status={}", request.eventId(), attempt, status);
                // Invalid requests/authentication errors are not transient.
                if (status < 500 && status != 429) return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); return false;
            } catch (Exception e) {
                // Do not log the body, token, recipient, or remote response.
                log.warn("notification_remote_failure eventId={} attempt={} reason={}",
                        request.eventId(), attempt, e.getClass().getSimpleName());
            }
            if (attempt < maxAttempts) {
                try { Thread.sleep(backoffMillis * attempt); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            }
        }
        return false;
    }
}
