package clinic;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentConfigurationTest {
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
