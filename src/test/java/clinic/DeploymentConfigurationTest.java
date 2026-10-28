package clinic;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentConfigurationTest {
    @Test void normalStartupUsesPersistentPostgresWithoutResetScripts() throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        assertTrue(properties.getProperty("spring.datasource.url").contains("jdbc:postgresql:"));
        assertEquals("org.postgresql.Driver", properties.getProperty("spring.datasource.driver-class-name"));
        assertEquals("never", properties.getProperty("spring.sql.init.mode"));
        assertEquals("true", properties.getProperty("spring.flyway.enabled"));
    }
    @Test void composeConfigurationIsValidYamlAndContainsAppAndDatabase() throws Exception {
        try (var input = Files.newInputStream(Path.of("docker-compose.yml"))) {
            Map<?, ?> config = new Yaml().load(input);
            var services = (Map<?, ?>) config.get("services");
            assertTrue(services.containsKey("app")); assertTrue(services.containsKey("db"));
            assertTrue(((Map<?, ?>) config.get("volumes")).containsKey("appointment-data"));
        }
    }
    @Test void ciConfigurationIsValidYamlAndDefinesTestJob() throws Exception {
        try (var input = Files.newInputStream(Path.of(".github/workflows/build.yml"))) {
            Map<?, ?> config = new Yaml().load(input);
            assertTrue(((Map<?, ?>) config.get("jobs")).containsKey("tests"));
        }
    }
}
