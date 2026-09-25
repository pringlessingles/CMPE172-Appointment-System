from pathlib import Path
import re
import sys

from pypdf import PdfReader


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
report_source = (ROOT / "docs/Milestone1_Report_Source.md").read_text()

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
    "CONSTRAINT uq_appointments_slot UNIQUE (availability_slot_id)" in schema,
    "Database-level double-booking guard is missing",
)
require("LIMIT :limit OFFSET :offset" in repository, "JDBC pagination is missing")
require('@GetMapping("/")' in controller, "GET / endpoint is missing")
require('@GetMapping("/slots")' in controller, "GET /slots endpoint is missing")
require(seed.count("'AVAILABLE'") >= 6, "Seed data needs at least six available slots")
require("Page Controller" in report_source and "Front Controller" in report_source, "Controller pattern discussion is missing")

pdf_expectations = {
    "CMPE172_Milestone1_Report_Newton_Tran.pdf": 11,
    "CMPE172_Milestone1_ER_Diagram_Newton_Tran.pdf": 1,
    "CMPE172_Milestone1_Wireframes_Newton_Tran.pdf": 4,
}
for filename, expected_pages in pdf_expectations.items():
    path = ROOT / "output/pdf" / filename
    require(path.exists() and path.stat().st_size > 10_000, f"Missing or undersized PDF: {filename}")
    if path.exists():
        reader = PdfReader(path)
        require(len(reader.pages) == expected_pages, f"Unexpected page count for {filename}")

if failures:
    for failure in failures:
        print(f"FAIL: {failure}")
    sys.exit(1)

print("PASS: project structure, schema constraints, endpoint markers, seed data, and PDFs verified")
