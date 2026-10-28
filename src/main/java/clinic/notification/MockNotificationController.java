package clinic.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
public class MockNotificationController {
    private static final Logger log = LoggerFactory.getLogger(MockNotificationController.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String token;
    public MockNotificationController(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${notifications.token}") String token) {
        this.jdbc = jdbc; this.mapper = mapper; this.token = token;
    }
    @PostMapping("/api/mock-notifications")
    public Map<String, Object> accept(@RequestHeader(value = "X-Notification-Token", defaultValue = "") String supplied,
            @Valid @RequestBody NotificationRequest request) throws Exception {
        if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid notification credentials");
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(request)));
        // The primary key arbitrates concurrent retries. This endpoint has no enclosing transaction,
        // so a duplicate INSERT does not leave PostgreSQL in a failed transaction.
        int inserted;
        try {
            inserted = jdbc.update("INSERT INTO notification_receipts(event_id, fingerprint) VALUES (?, ?)",
                    request.eventId().toString(), fingerprint);
        } catch (DuplicateKeyException e) { inserted = 0; }
        String stored = jdbc.queryForObject("SELECT fingerprint FROM notification_receipts WHERE event_id = ?",
                String.class, request.eventId().toString());
        if (!fingerprint.equals(stored)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Event ID already used");
        log.info("mock_notification_accepted eventId={} type={} duplicate={}", request.eventId(), request.type(), inserted == 0);
        return Map.of("status", "accepted", "duplicate", inserted == 0);
    }
}
