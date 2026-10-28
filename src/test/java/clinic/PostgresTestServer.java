package clinic;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.io.IOException;

// A real, disposable PostgreSQL instance for tests, not an in-memory replacement.
final class PostgresTestServer {
    private static EmbeddedPostgres local;
    static synchronized void configure(DynamicPropertyRegistry registry) throws IOException {
        String external = System.getenv("PG_TEST_URL");
        if (external == null || external.isBlank()) {
            if (local == null) local = EmbeddedPostgres.builder().setServerConfig("fsync", "on").start();
            registry.add("spring.datasource.url", () -> local.getJdbcUrl("postgres", "postgres"));
            registry.add("spring.datasource.username", () -> "postgres");
            registry.add("spring.datasource.password", () -> "postgres");
        } else {
            registry.add("spring.datasource.url", () -> external);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_USER", "appointments"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_PASSWORD", ""));
        }
        registry.add("notifications.token", () -> "test-notification-token");
    }
    static synchronized void close() throws IOException {
        if (local != null) { local.close(); local = null; }
    }
}
