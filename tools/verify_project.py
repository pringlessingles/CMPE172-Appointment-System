from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
failures = []


def require(condition, message):
    if not condition:
        failures.append(message)


pom = (ROOT / "pom.xml").read_text()
schema = (ROOT / "src/main/resources/schema.sql").read_text()
seed = (ROOT / "src/main/resources/seed.sql").read_text()
controller = (ROOT / "src/main/java/clinic/controller/ClinicController.java").read_text()
repository = (ROOT / "src/main/java/clinic/repository/ClinicRepository.java").read_text()

require("spring-boot-starter-jdbc" in pom, "Spring JDBC dependency is missing")
require("spring-boot-starter-data-jpa" not in pom, "JPA/ORM dependency must not be present")
require("edu.sjsu.cmpe172" not in pom, "School-specific Maven namespace is still present")
require((ROOT / "src/main/java/clinic/ClinicApplication.java").exists(), "Simplified clinic package is missing")

for source_root in (ROOT / "src/main/java", ROOT / "src/test/java"):
    for java_file in source_root.rglob("*.java"):
        package_match = re.search(r"^package\s+([\w.]+);", java_file.read_text(), re.MULTILINE)
        expected_package = ".".join(java_file.relative_to(source_root).parent.parts)
        require(package_match is not None, f"Missing package declaration: {java_file}")
        if package_match is not None:
            require(
                package_match.group(1) == expected_package,
                f"Package/path mismatch in {java_file}: expected {expected_package}",
            )

for table_name in ("users", "providers", "services", "availability_slots", "appointments"):
    require(
        re.search(rf"CREATE TABLE\s+{table_name}\s*\(", schema, re.IGNORECASE) is not None,
        f"Missing table: {table_name}",
    )

require(
    "CONSTRAINT uq_appointments_slot UNIQUE (active_slot_id)" in schema,
    "Database-level double-booking guard is missing",
)
require("LIMIT :limit OFFSET :offset" in repository, "JDBC pagination is missing")
require('@GetMapping("/api/home")' in controller, "GET /api/home endpoint is missing")
require('"/slots"' in controller and '"/api/slots"' in controller, "Slot endpoints are missing")
require(seed.count("'AVAILABLE'") >= 6, "Seed data needs at least six available slots")
require("spring-boot-starter-security" in pom, "Spring Security is missing")
require("spring-boot-starter-thymeleaf" in pom, "HTML rendering is missing")
require("version BIGINT" in schema, "Optimistic version column is missing")
require("GENERATED ALWAYS AS (CASE WHEN status = 'BOOKED'" in schema, "Active-only uniqueness column is missing")
for name in ("home", "login", "available", "book", "confirmation", "appointments", "doctor", "error"):
    require((ROOT / f"src/main/resources/templates/{name}.html").exists(), f"Missing page: {name}")

if failures:
    for failure in failures:
        print(f"FAIL: {failure}")
    sys.exit(1)

print("PASS: project structure, schema constraints, endpoint markers, seed data, and pages verified")
