from __future__ import annotations

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import LETTER, landscape
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfgen import canvas
from reportlab.platypus import (
    BaseDocTemplate,
    Flowable,
    Frame,
    KeepTogether,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output" / "pdf"
OUT.mkdir(parents=True, exist_ok=True)

NAVY = colors.HexColor("#17324D")
TEAL = colors.HexColor("#1F7A7A")
MINT = colors.HexColor("#E7F4F2")
SKY = colors.HexColor("#EAF3F8")
INK = colors.HexColor("#263238")
MUTED = colors.HexColor("#5E6B73")
LINE = colors.HexColor("#B9C8CE")
WHITE = colors.white
CORAL = colors.HexColor("#C95D4A")


def register_fonts() -> None:
    regular = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    bold = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
    if regular.exists() and bold.exists():
        pdfmetrics.registerFont(TTFont("DejaVu", str(regular)))
        pdfmetrics.registerFont(TTFont("DejaVu-Bold", str(bold)))


register_fonts()
FONT = "DejaVu" if "DejaVu" in pdfmetrics.getRegisteredFontNames() else "Helvetica"
BOLD = "DejaVu-Bold" if "DejaVu-Bold" in pdfmetrics.getRegisteredFontNames() else "Helvetica-Bold"


class ClinicDocTemplate(BaseDocTemplate):
    def __init__(self, filename: str):
        super().__init__(
            filename,
            pagesize=LETTER,
            leftMargin=0.68 * inch,
            rightMargin=0.68 * inch,
            topMargin=0.68 * inch,
            bottomMargin=0.58 * inch,
            title="CMPE 172 Milestone 1 - Doctor Appointment System",
            author="Newton Tran",
        )
        frame = Frame(
            self.leftMargin,
            self.bottomMargin,
            self.width,
            self.height,
            id="body",
        )
        self.addPageTemplates([PageTemplate(id="main", frames=frame, onPage=self._decorate)])

    def _decorate(self, c: canvas.Canvas, doc: BaseDocTemplate) -> None:
        c.saveState()
        c.setFillColor(NAVY)
        c.rect(0, LETTER[1] - 0.25 * inch, LETTER[0], 0.25 * inch, fill=1, stroke=0)
        if doc.page > 1:
            c.setFont(FONT, 7.5)
            c.setFillColor(MUTED)
            c.drawString(0.68 * inch, 0.31 * inch, "CMPE 172 | Newton Tran | 015110368")
            c.drawRightString(LETTER[0] - 0.68 * inch, 0.31 * inch, f"Page {doc.page}")
        c.restoreState()


class ArchitectureFlowable(Flowable):
    def __init__(self, width: float, height: float = 2.25 * inch):
        super().__init__()
        self.width = width
        self.height = height

    def draw(self) -> None:
        c = self.canv
        box_w = 1.22 * inch
        box_h = 0.62 * inch
        gap = 0.24 * inch
        total = box_w * 4 + gap * 3
        x = (self.width - total) / 2
        y = 1.15 * inch
        labels = ["Client", "Spring Boot\napplication", "JDBC\nrepository", "SQL database"]
        fills = [SKY, MINT, MINT, SKY]
        for idx, label in enumerate(labels):
            bx = x + idx * (box_w + gap)
            c.setFillColor(fills[idx])
            c.setStrokeColor(TEAL)
            c.roundRect(bx, y, box_w, box_h, 8, fill=1, stroke=1)
            c.setFont(BOLD, 9)
            c.setFillColor(NAVY)
            parts = label.split("\n")
            for row, text in enumerate(parts):
                c.drawCentredString(bx + box_w / 2, y + box_h / 2 + 4 - row * 11, text)
            if idx < len(labels) - 1:
                ax = bx + box_w
                c.setStrokeColor(NAVY)
                c.line(ax + 3, y + box_h / 2, ax + gap - 3, y + box_h / 2)
                c.line(ax + gap - 8, y + box_h / 2 + 4, ax + gap - 3, y + box_h / 2)
                c.line(ax + gap - 8, y + box_h / 2 - 4, ax + gap - 3, y + box_h / 2)

        c.setFillColor(colors.HexColor("#FFF4E8"))
        c.setStrokeColor(CORAL)
        mock_x = x + 1.55 * (box_w + gap)
        mock_y = 0.12 * inch
        c.roundRect(mock_x, mock_y, 2.3 * inch, 0.52 * inch, 8, fill=1, stroke=1)
        c.setFillColor(CORAL)
        c.setFont(BOLD, 8.5)
        c.drawCentredString(mock_x + 1.15 * inch, mock_y + 0.28 * inch, "Mock notification service boundary")
        c.setStrokeColor(CORAL)
        c.setDash(4, 3)
        c.line(mock_x + 0.2 * inch, mock_y + 0.52 * inch, x + 1.5 * (box_w + gap), y)
        c.setDash()
        c.setFont(FONT, 7.5)
        c.setFillColor(MUTED)
        c.drawCentredString(self.width / 2, 1.95 * inch, "HTTP/JSON          dispatch          parameterized SQL")


class RequestFlowable(Flowable):
    def __init__(self, width: float, height: float = 1.55 * inch):
        super().__init__()
        self.width = width
        self.height = height

    def draw(self) -> None:
        c = self.canv
        labels = ["DispatcherServlet", "Controller", "Service", "Repository", "H2"]
        box_w = 1.18 * inch
        gap = 0.18 * inch
        total = len(labels) * box_w + (len(labels) - 1) * gap
        x = (self.width - total) / 2
        y = 0.62 * inch
        for i, label in enumerate(labels):
            bx = x + i * (box_w + gap)
            c.setFillColor(SKY if i in (0, 4) else MINT)
            c.setStrokeColor(NAVY)
            c.roundRect(bx, y, box_w, 0.46 * inch, 6, fill=1, stroke=1)
            c.setFillColor(NAVY)
            c.setFont(BOLD, 7.8)
            c.drawCentredString(bx + box_w / 2, y + 0.22 * inch, label)
            if i < len(labels) - 1:
                c.line(bx + box_w + 3, y + 0.23 * inch, bx + box_w + gap - 3, y + 0.23 * inch)
                c.line(bx + box_w + gap - 7, y + 0.27 * inch, bx + box_w + gap - 3, y + 0.23 * inch)
                c.line(bx + box_w + gap - 7, y + 0.19 * inch, bx + box_w + gap - 3, y + 0.23 * inch)
        c.setFillColor(MUTED)
        c.setFont(FONT, 8)
        c.drawCentredString(self.width / 2, 0.25 * inch, "DTO JSON response returns through the same layers")


def styles():
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "Title", parent=base["Title"], fontName=BOLD, fontSize=25, leading=30,
            textColor=NAVY, alignment=TA_LEFT, spaceAfter=16,
        ),
        "subtitle": ParagraphStyle(
            "Subtitle", parent=base["Normal"], fontName=FONT, fontSize=11.5, leading=17,
            textColor=MUTED, spaceAfter=12,
        ),
        "h1": ParagraphStyle(
            "H1", parent=base["Heading1"], fontName=BOLD, fontSize=17, leading=21,
            textColor=NAVY, spaceBefore=6, spaceAfter=9,
        ),
        "h2": ParagraphStyle(
            "H2", parent=base["Heading2"], fontName=BOLD, fontSize=12.5, leading=16,
            textColor=TEAL, spaceBefore=8, spaceAfter=5,
        ),
        "body": ParagraphStyle(
            "Body", parent=base["BodyText"], fontName=FONT, fontSize=9.1, leading=13.4,
            textColor=INK, spaceAfter=6,
        ),
        "small": ParagraphStyle(
            "Small", parent=base["BodyText"], fontName=FONT, fontSize=7.5, leading=10.2,
            textColor=INK,
        ),
        "small_center": ParagraphStyle(
            "SmallCenter", parent=base["BodyText"], fontName=FONT, fontSize=7.5, leading=10,
            textColor=INK, alignment=TA_CENTER,
        ),
        "callout": ParagraphStyle(
            "Callout", parent=base["BodyText"], fontName=BOLD, fontSize=10.2, leading=14,
            textColor=NAVY, backColor=MINT, borderColor=TEAL, borderWidth=0.8,
            borderPadding=9, spaceBefore=6, spaceAfter=9,
        ),
        "code": ParagraphStyle(
            "Code", parent=base["Code"], fontName="Courier", fontSize=7.4, leading=10,
            textColor=INK, backColor=colors.HexColor("#F4F7F8"), borderPadding=7,
        ),
    }


def P(text: str, style) -> Paragraph:
    return Paragraph(text, style)


def table(data, widths, header=True, font_size=7.4):
    tbl = Table(data, colWidths=widths, repeatRows=1 if header else 0, hAlign="LEFT")
    commands = [
        ("FONTNAME", (0, 0), (-1, -1), FONT),
        ("FONTSIZE", (0, 0), (-1, -1), font_size),
        ("LEADING", (0, 0), (-1, -1), font_size + 3),
        ("TEXTCOLOR", (0, 0), (-1, -1), INK),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.45, LINE),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]
    if header:
        commands += [
            ("BACKGROUND", (0, 0), (-1, 0), NAVY),
            ("TEXTCOLOR", (0, 0), (-1, 0), WHITE),
            ("FONTNAME", (0, 0), (-1, 0), BOLD),
        ]
    for row in range(1 if header else 0, len(data)):
        if row % 2 == 0:
            commands.append(("BACKGROUND", (0, row), (-1, row), colors.HexColor("#F7FAFB")))
    tbl.setStyle(TableStyle(commands))
    return tbl


def build_report() -> Path:
    s = styles()
    path = OUT / "CMPE172_Milestone1_Report_Newton_Tran.pdf"
    doc = ClinicDocTemplate(str(path))
    story = []

    story += [
        Spacer(1, 0.65 * inch),
        P("CMPE 172", s["subtitle"]),
        P("Milestone 1", s["title"]),
        P("Requirements, Design &amp; Skeleton", ParagraphStyle(
            "CoverSub", parent=s["title"], fontSize=18, leading=23, textColor=TEAL
        )),
        Spacer(1, 0.18 * inch),
        P("Doctor Appointment System", ParagraphStyle(
            "Project", parent=s["subtitle"], fontName=BOLD, fontSize=14, leading=19, textColor=NAVY
        )),
        Spacer(1, 0.25 * inch),
        Table([
            [P("Student", s["small"]), P("Newton Tran", s["body"])],
            [P("Student ID", s["small"]), P("015110368", s["body"])],
            [P("Technology", s["small"]), P("Java 17 + Spring Boot + SQL via JDBC (no ORM)", s["body"])],
            [P("Milestone", s["small"]), P("Running layered read-only skeleton", s["body"])],
        ], colWidths=[1.2 * inch, 4.8 * inch], style=[
            ("BACKGROUND", (0, 0), (-1, -1), SKY),
            ("BOX", (0, 0), (-1, -1), 0.8, LINE),
            ("INNERGRID", (0, 0), (-1, -1), 0.45, LINE),
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("LEFTPADDING", (0, 0), (-1, -1), 8),
            ("TOPPADDING", (0, 0), (-1, -1), 7),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ]),
        Spacer(1, 0.75 * inch),
        P("Scope note", s["h2"]),
        P("This system schedules visits but intentionally stores no diagnoses, clinical notes, insurance records, or other protected health information. The milestone demonstrates architecture and scheduling data design.", s["callout"]),
        PageBreak(),
    ]

    story += [
        P("1. Proposal", s["h1"]),
        P("The Doctor Appointment System supports a medical office where patients browse appointment availability; in later milestones they will authenticate, book, review, and cancel their own appointments. Doctors publish service-specific availability and review visits assigned to them. Office administrators maintain scheduling reference data and operational oversight.", s["body"]),
        P("Technology statement", s["h2"]),
        P("The implementation uses <b>Java 17, Spring Boot, SQL, and direct JDBC through Spring JDBC</b>. It does not use JPA, Hibernate, or any other object-relational mapper.", s["callout"]),
        P("The project realizes classic enterprise standards on the modern stack: Spring Boot supplies a modern J2EE-style application container, while REST supplies distributed service boundaries analogous to CORBA/distributed objects.", s["body"]),
        P("User roles", s["h2"]),
        table([
            ["Role", "Responsibilities in the planned system"],
            ["Customer / patient", "Browse and filter slots; later log in, book, view, and cancel owned appointments."],
            ["Provider", "Publish service-specific availability; later log in and review appointments booked with them."],
            ["Administrator", "Maintain office reference data and provide operational oversight."],
        ], [1.3 * inch, 5.45 * inch], font_size=8),
        Spacer(1, 0.12 * inch),
        P("Main entities", s["h2"]),
        P("The required domain model contains <b>users</b>, <b>providers</b>, <b>services</b>, <b>availability_slots</b>, and <b>appointments</b>. Provider profiles extend users; providers offer services and publish slots; customers reserve slots through appointments.", s["body"]),
        P("Milestone 1 boundary", s["h2"]),
        P("Only the database-reading skeleton is implemented now. Authentication, writes, notifications, observability, and AI capabilities remain explicit roadmap items for Milestones 2-4.", s["body"]),
        PageBreak(),
    ]

    features = [
        ("1", "Customer log in", "M2"),
        ("2", "Browse/filter/paginate slots with SQL LIMIT and OFFSET", "M2; read skeleton in M1"),
        ("3", "Book a slot for a selected service", "M2"),
        ("4", "View upcoming appointments and history", "M2"),
        ("5", "Cancel an owned appointment", "M2"),
        ("6", "Provider log in", "M2"),
        ("7", "Provider creates/removes service-specific slots", "M2"),
        ("8", "Provider views appointments booked with them", "M2"),
        ("9", "Mock external confirmation on book/cancel", "M3"),
        ("10", "Logging, health endpoint, and metric", "M3"),
        ("11", "Conversational booking and autonomous agents", "M4"),
        ("12", "Grounded Q&amp;A over clinic knowledge", "M4"),
    ]
    story += [
        P("2. Feature Roadmap", s["h1"]),
        P("The proposal includes all twelve required features. The right column makes the milestone boundary visible.", s["body"]),
        table([["#", "Feature", "Planned milestone"]] + [[n, P(f, s["small"]), m] for n, f, m in features], [0.35 * inch, 4.9 * inch, 1.5 * inch], font_size=7.6),
        Spacer(1, 0.15 * inch),
        P("Milestone 1 delivers the architecture, schema, startup data, DTOs, and two read endpoints. The roadmap is documented so later work extends stable boundaries rather than replacing them.", s["callout"]),
        PageBreak(),
    ]

    story += [
        P("3. System Context and Boundary", s["h1"]),
        P("A client communicates with a Spring Boot REST application. The application dispatches requests through controller and service layers, and the repository uses JDBC to query the SQL database. A mock notification service is shown beyond the Milestone 1 runtime boundary; it will be called after booking or cancellation in Milestone 3.", s["body"]),
        Spacer(1, 0.18 * inch),
        ArchitectureFlowable(doc.width),
        Spacer(1, 0.15 * inch),
        P("Boundary decisions", s["h2"]),
        table([
            ["Inside Milestone 1", "Outside Milestone 1"],
            ["REST endpoints and DTO serialization", "Authentication and authorization"],
            ["Controller, service, JDBC repository", "Booking and cancellation commands"],
            ["H2 schema, constraints, and seed data", "Mock notification invocation"],
            ["Filter and pagination SQL", "Metrics, AI agents, and grounded Q&amp;A"],
        ], [3.35 * inch, 3.4 * inch], font_size=8),
        Spacer(1, 0.15 * inch),
        P("Privacy-oriented scope", s["h2"]),
        P("The model records scheduling metadata only. It avoids diagnoses, treatment notes, medical record numbers, insurance details, and free-text health information. Future production work would require security, consent, retention, audit, and regulatory review beyond this academic skeleton.", s["body"]),
        PageBreak(),
    ]

    story += [
        P("4. ER / EER Design", s["h1"]),
        P("The standalone ER diagram accompanies this report. The relationships and cardinalities are summarized below.", s["body"]),
        table([
            ["Relationship", "Cardinality", "Rationale"],
            ["users to providers", "1 to 0..1", "A provider profile belongs to exactly one user; a customer/admin user need not be a provider."],
            ["providers to services", "1 to many", "Each offered service has one provider; a provider can offer multiple services."],
            ["providers to availability_slots", "1 to many", "Each slot has one provider; a provider can publish many slots."],
            ["services to availability_slots", "1 to many", "Each slot is tied to one service; a service can have many slots."],
            ["users to appointments", "1 to many", "Each appointment has one customer user; a customer can have many appointments."],
            ["availability_slots to appointments", "1 to 0..1", "A slot may be unbooked or have exactly one appointment."],
        ], [1.85 * inch, 1.05 * inch, 3.85 * inch], font_size=7.4),
        P("Weak entities and identity", s["h2"]),
        P("No table is a classical weak entity because every table has a surrogate BIGINT primary key. However, provider is existence-dependent on user through a mandatory and unique foreign key. Appointments are existence-dependent on customer, provider, service, and slot rows through mandatory foreign keys.", s["body"]),
        P("Consistency note", s["h2"]),
        P("An appointment repeats provider_id and service_id for direct integrity checks and future reporting. Milestone 2 booking logic must verify that those values match the referenced slot in the same transaction. The authoritative scheduling unit remains availability_slot_id.", s["body"]),
        PageBreak(),
    ]

    schema_rows = [
        ["Table", "Primary key", "Foreign keys", "Important constraints"],
        ["users", "id", "-", "email UNIQUE; role CHECK; required name and password hash"],
        ["providers", "id", "user_id -> users.id", "user_id UNIQUE; active required"],
        ["services", "id", "provider_id -> providers.id", "provider/name UNIQUE; duration and price CHECK"],
        ["availability_slots", "id", "provider_id -> providers.id; service_id -> services.id", "provider/start UNIQUE; ends_at > starts_at; status CHECK"],
        ["appointments", "id", "customer_user_id -> users.id; provider_id -> providers.id; service_id -> services.id; availability_slot_id -> availability_slots.id", "availability_slot_id UNIQUE; status CHECK"],
    ]
    story += [
        P("5. Relational Schema", s["h1"]),
        table([[P(cell, s["small"]) for cell in row] for row in schema_rows], [1.05 * inch, 0.7 * inch, 2.8 * inch, 2.2 * inch], font_size=6.7),
        Spacer(1, 0.14 * inch),
        P("Double-booking guard", s["h2"]),
        P("<b>CONSTRAINT uq_appointments_slot UNIQUE (availability_slot_id)</b>", s["callout"]),
        P("The unique constraint makes one slot correspond to at most one appointment row. It is enforced inside the database, so simultaneous requests cannot both commit appointments for the same slot. Application-side checks improve error messages but are not a substitute for this constraint.", s["body"]),
        P("Additional scheduling guard", s["h2"]),
        P("<b>UNIQUE (provider_id, starts_at)</b> on availability_slots prevents a provider from publishing two slots with the same start time. A production version can strengthen overlap detection for differently sized intervals through a transaction or database-specific exclusion constraint.", s["body"]),
        P("Indexes", s["h2"]),
        P("The slot search index begins with status and starts_at, followed by provider_id and service_id. Appointment indexes support customer and provider timelines. Primary and unique constraints create their own indexes in H2.", s["body"]),
        PageBreak(),
    ]

    story += [
        P("6. Wireframes", s["h1"]),
        P("The accompanying Wireframes PDF contains four labeled screens. The wireframes describe later user interaction even though Milestone 1 exposes JSON only.", s["body"]),
        table([
            ["Screen", "Purpose", "Primary elements"],
            ["Home", "Orient the patient and begin discovery", "Doctor office identity, service summary, Browse appointments action"],
            ["View available slots", "Find an appropriate appointment", "Provider/service/date filters, slot cards, pagination"],
            ["Book appointment", "Confirm the selected slot", "Read-only slot summary, patient contact fields, consent checkbox"],
            ["Confirmation", "Communicate successful booking", "Confirmation number, visit details, next actions, mock-notification status"],
        ], [1.4 * inch, 2.0 * inch, 3.35 * inch], font_size=8),
        Spacer(1, 0.2 * inch),
        P("Design principles", s["h2"]),
        P("The flow exposes one primary action per page, keeps appointment details visible before confirmation, and separates filtering from booking. Clinical details are not collected. Error, empty, and loading states will be added when the UI is implemented.", s["body"]),
        P("Accessibility considerations", s["h2"]),
        P("Controls use explicit labels rather than placeholder-only text. Color is not the only status cue. Buttons are large enough for touch interaction. A production UI will require keyboard testing, focus order, screen-reader labels, contrast verification, and localized date/time presentation.", s["body"]),
        Spacer(1, 0.25 * inch),
        P("Navigation flow", s["h2"]),
        P("Home -> Available slots -> Book appointment -> Confirmation", s["callout"]),
        PageBreak(),
    ]

    story += [
        P("7. Layered Skeleton and Request Flow", s["h1"]),
        P("The code uses constructor injection and separates transport, orchestration, persistence, and response contracts. No persistence entity is serialized directly.", s["body"]),
        RequestFlowable(doc.width),
        P("GET /slots sequence", s["h2"]),
        table([
            ["Step", "Layer", "Responsibility"],
            ["1", "DispatcherServlet", "Receives the HTTP request and selects ClinicController."],
            ["2", "ClinicController", "Parses providerId, serviceId, date, page, and size."],
            ["3", "ClinicService", "Validates bounds, calculates offset, and constructs page metadata."],
            ["4", "ClinicRepository", "Runs parameterized JDBC queries with LIMIT and OFFSET."],
            ["5", "H2", "Applies relational constraints and returns matching rows."],
            ["6", "DTO response", "Returns immutable slot data and pagination metadata as JSON."],
        ], [0.45 * inch, 1.35 * inch, 4.95 * inch], font_size=7.8),
        P("Required endpoints", s["h2"]),
        P("<b>GET /</b> reads active providers and services. <b>GET /slots</b> reads available slots and supports optional provider, service, and date filters plus page and size parameters.", s["body"]),
        PageBreak(),
    ]

    story += [
        P("8. Page Controller vs Front Controller", s["h1"]),
        table([
            ["Aspect", "Page Controller", "Front Controller"],
            ["Entry point", "A handler focused on a particular rendered page", "One central entry point dispatches requests"],
            ["Typical Spring example", "A Thymeleaf controller method returning a template", "DispatcherServlet routing REST/API requests"],
            ["Best fit", "Server-rendered pages with page-specific preparation", "Consistent cross-cutting handling and API routing"],
            ["This project", "Not selected for the read skeleton", "Selected: REST endpoints behind DispatcherServlet"],
        ], [1.25 * inch, 2.7 * inch, 2.8 * inch], font_size=8),
        Spacer(1, 0.15 * inch),
        P("Decision", s["h2"]),
        P("This project uses Spring's <b>DispatcherServlet as a Front Controller</b>. It centralizes request dispatch and supports a clean REST boundary for a separate browser client. If a later milestone uses server-rendered Thymeleaf, page-focused controller methods can coexist while the DispatcherServlet still provides framework-level front control.", s["callout"]),
        P("Why DTOs", s["h2"]),
        P("DTO records stabilize the external contract and prevent table-specific details such as password hashes from leaking. They also make response fields deliberate and easy to test.", s["body"]),
        P("Why JDBC", s["h2"]),
        P("NamedParameterJdbcTemplate preserves explicit SQL while removing connection and statement boilerplate. The repository controls joins, filters, ordering, LIMIT, and OFFSET directly, satisfying the no-ORM requirement.", s["body"]),
        PageBreak(),
    ]

    story += [
        P("9. Verification and Run Instructions", s["h1"]),
        P("Startup initialization", s["h2"]),
        P("Spring Boot runs schema.sql and seed.sql because spring.sql.init.mode is set to always. The schema recreates all five tables, and the seed script loads two providers, four services, six available slots, one blocked slot, a customer, and an administrator.", s["body"]),
        P("Local commands", s["h2"]),
        P("mvn spring-boot:run<br/>mvn test<br/>curl http://localhost:8080/<br/>curl &quot;http://localhost:8080/slots?providerId=1&amp;date=2026-10-05&amp;page=0&amp;size=1&quot;", s["code"]),
        P("Automated checks", s["h2"]),
        table([
            ["Test", "What it verifies"],
            ["homeReadsProvidersAndServicesFromDatabase", "The application starts with SQL data and GET / returns two providers and four services."],
            ["slotsSupportsFilteringAndPagination", "Provider/date filters, LIMIT/OFFSET page shape, total count, and DTO mapping."],
            ["invalidPageSizeReturnsBadRequest", "Service validation and consistent JSON error response."],
        ], [2.55 * inch, 4.2 * inch], font_size=7.6),
        P("Environment note", s["h2"]),
        P("The project targets Java 17 and Maven 3.9+. H2 is in-memory and intended only for this skeleton. A later deployment can switch to PostgreSQL while keeping the controller and service contracts stable.", s["body"]),
        PageBreak(),
    ]

    story += [
        P("10. Conclusion and Next Steps", s["h1"]),
        P("Milestone 1 establishes a coherent scope, a constrained five-entity schema, user-flow wireframes, and a correctly layered Spring Boot/JDBC skeleton. The database, not only the application, guarantees that one availability slot cannot be booked twice.", s["body"]),
        P("Next milestone priorities", s["h2"]),
        table([
            ["Priority", "Planned work"],
            ["Authentication", "Customer and provider login with role-aware authorization."],
            ["Booking transaction", "Atomically create an appointment, handle the unique-constraint conflict, and expose a clear result."],
            ["Ownership", "Allow customers to cancel only their own appointments."],
            ["Provider tools", "Create/remove slots and view booked appointments."],
            ["UI", "Implement the documented four-screen flow against the REST API."],
        ], [1.35 * inch, 5.4 * inch], font_size=8),
        Spacer(1, 0.2 * inch),
        P("Submission contents", s["h2"]),
        P("The package includes this report, the standalone ER diagram, the wireframes, source code, schema.sql, seed.sql, integration tests, a README, a submission checklist, and a minimum five-minute walkthrough script.", s["callout"]),
        P("Newton Tran | Student ID: 015110368", ParagraphStyle(
            "Signature", parent=s["body"], fontName=BOLD, textColor=NAVY, spaceBefore=18
        )),
    ]

    doc.build(story)
    return path


def entity_box(c, x, y, w, h, title, rows, highlight=False):
    c.setFillColor(TEAL if highlight else NAVY)
    c.setStrokeColor(NAVY)
    c.roundRect(x, y, w, h, 7, fill=0, stroke=1)
    c.roundRect(x, y + h - 0.34 * inch, w, 0.34 * inch, 7, fill=1, stroke=0)
    c.rect(x, y + h - 0.34 * inch, w, 0.17 * inch, fill=1, stroke=0)
    c.setFillColor(WHITE)
    c.setFont(BOLD, 9)
    c.drawString(x + 7, y + h - 0.23 * inch, title)
    c.setFont(FONT, 6.6)
    c.setFillColor(INK)
    cursor = y + h - 0.50 * inch
    for row in rows:
        c.drawString(x + 7, cursor, row)
        cursor -= 0.16 * inch


def relationship(c, x1, y1, x2, y2, label, left="1", right="N"):
    c.setStrokeColor(MUTED)
    c.setFillColor(MUTED)
    c.setLineWidth(0.8)
    c.line(x1, y1, x2, y2)
    if abs(y2 - y1) < 2:
        c.setFont(FONT, 6.5)
        c.drawCentredString((x1 + x2) / 2, y1 + 9, label)
        c.setFont(BOLD, 7)
        c.drawString(x1 + 3, y1 - 11, left)
        c.drawRightString(x2 - 3, y2 - 11, right)
    elif abs(x2 - x1) < 2:
        high_y, low_y = max(y1, y2), min(y1, y2)
        c.setFont(FONT, 6.5)
        c.drawString(x1 + 6, (high_y + low_y) / 2 - 2, label)
        c.setFont(BOLD, 7)
        c.drawString(x1 + 5, high_y - 11, left)
        c.drawString(x2 - 13, low_y + 4, right)
    else:
        c.setFont(FONT, 6.5)
        c.drawString((x1 + x2) / 2 - 6, (y1 + y2) / 2 + 7, label)
        c.setFont(BOLD, 7)
        c.drawString(x1 + 4, y1 - 10, left)
        c.drawString(x2 - 13, y2 + 5, right)


def build_er_diagram() -> Path:
    path = OUT / "CMPE172_Milestone1_ER_Diagram_Newton_Tran.pdf"
    c = canvas.Canvas(str(path), pagesize=landscape(LETTER))
    width, height = landscape(LETTER)
    c.setTitle("Doctor Appointment System ER Diagram")
    c.setAuthor("Newton Tran")
    c.setFillColor(NAVY)
    c.rect(0, height - 0.5 * inch, width, 0.5 * inch, fill=1, stroke=0)
    c.setFillColor(WHITE)
    c.setFont(BOLD, 16)
    c.drawString(0.45 * inch, height - 0.34 * inch, "Doctor Appointment System - ER / EER Diagram")
    c.setFont(FONT, 8)
    c.drawRightString(width - 0.45 * inch, height - 0.32 * inch, "Newton Tran | 015110368")

    bw, bh = 1.75 * inch, 1.42 * inch
    entity_box(c, 0.38 * inch, 3.32 * inch, bw, bh, "users", [
        "PK id", "UQ email", "password_hash", "full_name", "role", "created_at"
    ])
    entity_box(c, 2.75 * inch, 3.32 * inch, bw, bh, "providers", [
        "PK id", "FK/UQ user_id", "specialty", "active"
    ])
    entity_box(c, 5.05 * inch, 3.32 * inch, bw, bh, "services", [
        "PK id", "FK provider_id", "name", "description", "duration_minutes", "price, active"
    ])
    entity_box(c, 5.05 * inch, 0.72 * inch, bw, 1.58 * inch, "availability_slots", [
        "PK id", "FK provider_id", "FK service_id", "starts_at", "ends_at", "status"
    ], highlight=True)
    entity_box(c, 2.75 * inch, 0.72 * inch, bw, 1.58 * inch, "appointments", [
        "PK id", "FK customer_user_id", "FK provider_id", "FK service_id", "FK/UQ availability_slot_id", "status, booked_at"
    ], highlight=True)

    relationship(c, 2.13 * inch, 4.03 * inch, 2.75 * inch, 4.03 * inch, "profile", "1", "0..1")
    relationship(c, 4.50 * inch, 4.03 * inch, 5.05 * inch, 4.03 * inch, "offers", "1", "N")
    relationship(c, 5.93 * inch, 3.32 * inch, 5.93 * inch, 2.30 * inch, "has slots", "1", "N")
    relationship(c, 4.50 * inch, 1.51 * inch, 5.05 * inch, 1.51 * inch, "reserves", "0..1", "1")
    relationship(c, 2.13 * inch, 3.55 * inch, 2.75 * inch, 1.95 * inch, "books", "1", "N")
    relationship(c, 3.63 * inch, 3.32 * inch, 3.63 * inch, 2.30 * inch, "receives", "1", "N")

    c.setFillColor(MINT)
    c.setStrokeColor(TEAL)
    c.roundRect(7.18 * inch, 0.72 * inch, 3.35 * inch, 4.02 * inch, 8, fill=1, stroke=1)
    c.setFillColor(NAVY)
    c.setFont(BOLD, 10)
    c.drawString(7.38 * inch, 4.43 * inch, "Key integrity rules")
    notes = [
        "appointments.availability_slot_id UNIQUE",
        "  prevents two appointments for one slot",
        "availability_slots(provider_id, starts_at) UNIQUE",
        "  prevents duplicate provider start times",
        "provider.user_id UNIQUE",
        "  gives a user at most one provider profile",
        "CHECK ends_at > starts_at",
        "CHECK role and status value sets",
        "Mandatory FKs preserve referential integrity",
    ]
    c.setFont(FONT, 7.6)
    cursor = 4.12 * inch
    for note in notes:
        c.drawString(7.38 * inch, cursor, note)
        cursor -= 0.27 * inch
    c.setFillColor(WHITE)
    c.setStrokeColor(NAVY)
    c.roundRect(7.38 * inch, 1.02 * inch, 2.95 * inch, 0.72 * inch, 6, fill=1, stroke=1)
    c.setFillColor(CORAL)
    c.setFont(BOLD, 8)
    c.drawCentredString(8.86 * inch, 1.47 * inch, "Double-booking defense")
    c.setFillColor(INK)
    c.setFont(FONT, 7)
    c.drawCentredString(8.86 * inch, 1.24 * inch, "Database uniqueness remains authoritative")

    c.setFillColor(MUTED)
    c.setFont(FONT, 6.8)
    c.drawString(0.45 * inch, 0.30 * inch, "PK = primary key | FK = foreign key | UQ = unique | Cardinalities are shown at relationship ends")
    c.save()
    return path


def wireframe_chrome(c, title, step, width, height):
    c.setFillColor(colors.HexColor("#F5F8F9"))
    c.rect(0, 0, width, height, fill=1, stroke=0)
    c.setFillColor(NAVY)
    c.rect(0, height - 0.68 * inch, width, 0.68 * inch, fill=1, stroke=0)
    c.setFillColor(WHITE)
    c.setFont(BOLD, 14)
    c.drawString(0.48 * inch, height - 0.43 * inch, "Doctor Appointment System")
    c.setFont(FONT, 8)
    c.drawRightString(width - 0.48 * inch, height - 0.40 * inch, f"Wireframe {step}/4 - {title}")
    c.setFillColor(WHITE)
    c.setStrokeColor(LINE)
    c.roundRect(0.46 * inch, 0.48 * inch, width - 0.92 * inch, height - 1.42 * inch, 9, fill=1, stroke=1)


def label(c, x, y, text, size=8, bold=False, color=INK):
    c.setFillColor(color)
    c.setFont(BOLD if bold else FONT, size)
    c.drawString(x, y, text)


def button(c, x, y, w, text, primary=True):
    c.setFillColor(TEAL if primary else WHITE)
    c.setStrokeColor(TEAL)
    c.roundRect(x, y, w, 0.38 * inch, 7, fill=1, stroke=1)
    c.setFillColor(WHITE if primary else TEAL)
    c.setFont(BOLD, 8)
    c.drawCentredString(x + w / 2, y + 0.14 * inch, text)


def input_box(c, x, y, w, field_label, value=""):
    label(c, x, y + 0.41 * inch, field_label, 7.5, True, MUTED)
    c.setFillColor(WHITE)
    c.setStrokeColor(LINE)
    c.roundRect(x, y, w, 0.34 * inch, 4, fill=1, stroke=1)
    if value:
        label(c, x + 6, y + 0.12 * inch, value, 7.5, False, INK)


def build_wireframes() -> Path:
    path = OUT / "CMPE172_Milestone1_Wireframes_Newton_Tran.pdf"
    page = landscape(LETTER)
    width, height = page
    c = canvas.Canvas(str(path), pagesize=page)
    c.setTitle("Doctor Appointment System Wireframes")
    c.setAuthor("Newton Tran")

    # 1. Home
    wireframe_chrome(c, "Home", 1, width, height)
    label(c, 0.85 * inch, 5.35 * inch, "Care that fits your schedule", 23, True, NAVY)
    label(c, 0.86 * inch, 4.96 * inch, "Find an appointment with a doctor at our medical office.", 9, False, MUTED)
    button(c, 0.86 * inch, 4.25 * inch, 1.75 * inch, "Browse appointments")
    c.setFillColor(SKY)
    c.setStrokeColor(LINE)
    c.roundRect(6.8 * inch, 3.52 * inch, 3.4 * inch, 2.1 * inch, 12, fill=1, stroke=1)
    c.setFillColor(TEAL)
    c.circle(8.5 * inch, 4.58 * inch, 0.48 * inch, fill=0, stroke=1)
    c.line(8.5 * inch, 4.34 * inch, 8.5 * inch, 4.82 * inch)
    c.line(8.26 * inch, 4.58 * inch, 8.74 * inch, 4.58 * inch)
    for idx, (head, body) in enumerate([
        ("Primary care", "Routine evaluation and follow-up"),
        ("Wellness", "Preventive exams and screening plans"),
        ("Convenient times", "Filter by provider, service, and date"),
    ]):
        x = 0.86 * inch + idx * 3.45 * inch
        c.setFillColor(WHITE)
        c.setStrokeColor(LINE)
        c.roundRect(x, 1.15 * inch, 3.0 * inch, 1.5 * inch, 8, fill=1, stroke=1)
        label(c, x + 0.18 * inch, 2.25 * inch, head, 10, True, NAVY)
        label(c, x + 0.18 * inch, 1.88 * inch, body, 7.2, False, MUTED)
    c.showPage()

    # 2. Available slots
    wireframe_chrome(c, "View available slots", 2, width, height)
    label(c, 0.82 * inch, 5.38 * inch, "Available appointments", 17, True, NAVY)
    input_box(c, 0.82 * inch, 4.56 * inch, 2.25 * inch, "Provider", "All providers  v")
    input_box(c, 3.28 * inch, 4.56 * inch, 2.25 * inch, "Service", "All services  v")
    input_box(c, 5.74 * inch, 4.56 * inch, 2.0 * inch, "Date", "2026-10-05")
    button(c, 7.96 * inch, 4.56 * inch, 1.25 * inch, "Apply filters")
    for idx, (time, provider, service) in enumerate([
        ("9:00 AM", "Dr. Maya Chen", "General Consultation"),
        ("9:20 AM", "Dr. Jordan Lee", "Vaccination Appointment"),
        ("10:00 AM", "Dr. Maya Chen", "Annual Wellness Exam"),
    ]):
        y = 3.55 * inch - idx * 0.92 * inch
        c.setFillColor(WHITE)
        c.setStrokeColor(LINE)
        c.roundRect(0.82 * inch, y, 9.4 * inch, 0.70 * inch, 7, fill=1, stroke=1)
        label(c, 1.03 * inch, y + 0.41 * inch, time, 10, True, NAVY)
        label(c, 2.38 * inch, y + 0.43 * inch, provider, 8.2, True, INK)
        label(c, 2.38 * inch, y + 0.20 * inch, service, 7.4, False, MUTED)
        button(c, 8.92 * inch, y + 0.16 * inch, 1.05 * inch, "Select")
    label(c, 0.86 * inch, 0.84 * inch, "Showing 1-3 of 6", 7.5, False, MUTED)
    button(c, 8.20 * inch, 0.72 * inch, 0.82 * inch, "Previous", False)
    button(c, 9.18 * inch, 0.72 * inch, 0.82 * inch, "Next", False)
    c.showPage()

    # 3. Booking form
    wireframe_chrome(c, "Book appointment", 3, width, height)
    label(c, 0.82 * inch, 5.38 * inch, "Confirm your appointment", 17, True, NAVY)
    c.setFillColor(MINT)
    c.setStrokeColor(TEAL)
    c.roundRect(0.82 * inch, 3.76 * inch, 3.1 * inch, 1.22 * inch, 8, fill=1, stroke=1)
    label(c, 1.03 * inch, 4.61 * inch, "Selected visit", 8, True, TEAL)
    label(c, 1.03 * inch, 4.30 * inch, "General Consultation", 10, True, NAVY)
    label(c, 1.03 * inch, 4.04 * inch, "Dr. Maya Chen | Oct 5, 2026 | 9:00 AM", 7.4, False, INK)
    input_box(c, 4.42 * inch, 4.55 * inch, 2.65 * inch, "Full name", "Sofia Reyes")
    input_box(c, 7.30 * inch, 4.55 * inch, 2.65 * inch, "Email", "sofia.reyes@example.com")
    input_box(c, 4.42 * inch, 3.60 * inch, 2.65 * inch, "Phone (optional)", "(408) 555-0142")
    input_box(c, 7.30 * inch, 3.60 * inch, 2.65 * inch, "Preferred contact", "Email  v")
    c.setFillColor(WHITE)
    c.setStrokeColor(LINE)
    c.rect(4.42 * inch, 2.95 * inch, 0.18 * inch, 0.18 * inch, fill=1, stroke=1)
    label(c, 4.70 * inch, 2.98 * inch, "I confirm the date, time, service, and contact information.", 7.4, False, INK)
    c.setFillColor(colors.HexColor("#FFF4E8"))
    c.setStrokeColor(CORAL)
    c.roundRect(4.42 * inch, 2.05 * inch, 5.53 * inch, 0.55 * inch, 6, fill=1, stroke=1)
    label(c, 4.62 * inch, 2.35 * inch, "Do not enter symptoms or private medical information in this scheduling form.", 7.2, True, CORAL)
    button(c, 4.42 * inch, 1.25 * inch, 1.35 * inch, "Back", False)
    button(c, 8.05 * inch, 1.25 * inch, 1.90 * inch, "Book appointment")
    c.showPage()

    # 4. Confirmation
    wireframe_chrome(c, "Confirmation", 4, width, height)
    c.setFillColor(MINT)
    c.setStrokeColor(TEAL)
    c.circle(2.05 * inch, 4.62 * inch, 0.43 * inch, fill=1, stroke=1)
    c.setStrokeColor(WHITE)
    c.setLineWidth(3)
    c.line(1.84 * inch, 4.62 * inch, 1.99 * inch, 4.46 * inch)
    c.line(1.99 * inch, 4.46 * inch, 2.30 * inch, 4.81 * inch)
    label(c, 2.76 * inch, 4.85 * inch, "Appointment confirmed", 20, True, NAVY)
    label(c, 2.77 * inch, 4.46 * inch, "Confirmation CLC-2026-1005-001", 8.5, False, MUTED)
    c.setFillColor(WHITE)
    c.setStrokeColor(LINE)
    c.roundRect(2.76 * inch, 2.18 * inch, 5.6 * inch, 1.72 * inch, 8, fill=1, stroke=1)
    rows = [
        ("Service", "General Consultation"),
        ("Provider", "Dr. Maya Chen"),
        ("Date and time", "October 5, 2026 at 9:00 AM"),
        ("Notification", "Confirmation queued through mock service"),
    ]
    for idx, (head, value) in enumerate(rows):
        y = 3.55 * inch - idx * 0.34 * inch
        label(c, 3.02 * inch, y, head, 7.3, True, MUTED)
        label(c, 4.48 * inch, y, value, 7.8, False, INK)
    button(c, 2.76 * inch, 1.28 * inch, 1.75 * inch, "View appointments")
    button(c, 4.72 * inch, 1.28 * inch, 1.55 * inch, "Back to home", False)
    label(c, 2.77 * inch, 0.90 * inch, "Bring an ID and arrive 10 minutes early. Call the clinic for urgent changes.", 7.2, False, MUTED)
    c.showPage()

    c.save()
    return path


if __name__ == "__main__":
    generated = [build_report(), build_er_diagram(), build_wireframes()]
    for file_path in generated:
        print(file_path)
