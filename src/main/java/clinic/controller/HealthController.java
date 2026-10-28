package clinic.controller;

import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
            if (!Integer.valueOf(1).equals(result)) throw new IllegalStateException("Database probe failed");
            return ResponseEntity.ok(Map.of("status", "UP", "database", "UP"));
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).error("health_database_down reason={}", e.getClass().getSimpleName());
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "database", "DOWN"));
        }
    }
}
