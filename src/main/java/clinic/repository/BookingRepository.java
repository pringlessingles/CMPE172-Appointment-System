package clinic.repository;

import clinic.dto.AppointmentDto;
import clinic.dto.SlotRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class BookingRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public BookingRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SLOT_SELECT = """
        SELECT s.*, u.full_name AS provider_name, v.name AS service_name,
               p.active AS provider_active, v.active AS service_active
        FROM availability_slots s JOIN providers p ON p.id = s.provider_id
        JOIN users u ON u.id = p.user_id JOIN services v ON v.id = s.service_id
        """;
    private static final RowMapper<SlotRecord> SLOT_MAPPER = (rs, n) -> new SlotRecord(
        rs.getLong("id"), rs.getLong("provider_id"), rs.getLong("service_id"),
        rs.getTimestamp("starts_at").toLocalDateTime(), rs.getTimestamp("ends_at").toLocalDateTime(),
        rs.getString("status"), rs.getLong("version"), rs.getString("provider_name"),
        rs.getString("service_name"), rs.getBoolean("provider_active"), rs.getBoolean("service_active"));
    private static final String APPOINTMENT_SELECT = """
        SELECT a.*, patient.full_name AS patient_name, doctor.full_name AS provider_name,
               v.name AS service_name, s.starts_at, s.ends_at,
               CASE WHEN a.status = 'BOOKED' AND s.ends_at <= CURRENT_TIMESTAMP
                    THEN 'COMPLETED' ELSE a.status END AS display_status
        FROM appointments a JOIN users patient ON patient.id = a.customer_user_id
        JOIN providers p ON p.id = a.provider_id JOIN users doctor ON doctor.id = p.user_id
        JOIN services v ON v.id = a.service_id JOIN availability_slots s ON s.id = a.availability_slot_id
        """;
    private static final RowMapper<AppointmentDto> APPOINTMENT_MAPPER = (rs, n) -> new AppointmentDto(
        rs.getLong("id"), rs.getLong("customer_user_id"), rs.getLong("provider_id"),
        rs.getLong("service_id"), rs.getLong("availability_slot_id"), rs.getString("patient_name"),
        rs.getString("provider_name"), rs.getString("service_name"),
        rs.getTimestamp("starts_at").toLocalDateTime(), rs.getTimestamp("ends_at").toLocalDateTime(),
        rs.getString("display_status"));

    public Optional<SlotRecord> slot(long id) {
        return jdbc.query(SLOT_SELECT + " WHERE s.id = :id", Map.of("id", id), SLOT_MAPPER).stream().findFirst();
    }
    public List<SlotRecord> providerSlots(long providerId) {
        return jdbc.query(SLOT_SELECT + " WHERE p.id = :id ORDER BY s.starts_at, s.id",
                Map.of("id", providerId), SLOT_MAPPER);
    }
    public Optional<Long> providerForUser(long userId) {
        return jdbc.query("SELECT id FROM providers WHERE user_id = :id AND active = TRUE",
                Map.of("id", userId), (rs, n) -> rs.getLong("id")).stream().findFirst();
    }
    public boolean serviceBelongsTo(long serviceId, long providerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM services WHERE id = :s AND provider_id = :p AND active = TRUE",
                Map.of("s", serviceId, "p", providerId), Integer.class) == 1;
    }
    // Serialize schedule changes per doctor, not globally, for concurrent requests.
    public void lockProvider(long providerId) {
        jdbc.queryForObject("SELECT id FROM providers WHERE id = :id FOR UPDATE",
                Map.of("id", providerId), Long.class);
    }
    public boolean overlappingSlot(long providerId, LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM availability_slots
                WHERE provider_id = :p AND status IN ('AVAILABLE', 'BOOKED')
                  AND starts_at < :end AND ends_at > :start
                """, Map.of("p", providerId, "start", start, "end", end), Integer.class) > 0;
    }
    public boolean overlappingBooking(SlotRecord slot) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointments a
                JOIN availability_slots s ON s.id = a.availability_slot_id
                WHERE a.provider_id = :p AND a.status = 'BOOKED' AND s.id <> :id
                  AND s.starts_at < :end AND s.ends_at > :start
                """, Map.of("p", slot.providerId(), "id", slot.id(),
                        "start", slot.startsAt(), "end", slot.endsAt()), Integer.class) > 0;
    }
    public int claim(long id, long version) {
        return jdbc.update("""
            UPDATE availability_slots SET status = 'BOOKED', version = version + 1
            WHERE id = :id AND version = :version AND status = 'AVAILABLE' AND starts_at > CURRENT_TIMESTAMP
            """, Map.of("id", id, "version", version));
    }
    public long insertAppointment(long customerId, SlotRecord slot) {
        var key = new GeneratedKeyHolder();
        jdbc.update("""
            INSERT INTO appointments (customer_user_id, provider_id, service_id, availability_slot_id, status)
            VALUES (:c, :p, :s, :slot, 'BOOKED')
            """, new MapSqlParameterSource().addValue("c", customerId).addValue("p", slot.providerId())
                .addValue("s", slot.serviceId()).addValue("slot", slot.id()), key, new String[]{"id"});
        return key.getKey().longValue();
    }
    public Optional<AppointmentDto> appointment(long id) {
        return jdbc.query(APPOINTMENT_SELECT + " WHERE a.id = :id", Map.of("id", id), APPOINTMENT_MAPPER).stream().findFirst();
    }
    public List<AppointmentDto> customerAppointments(long userId) {
        return jdbc.query(APPOINTMENT_SELECT + " WHERE a.customer_user_id = :id ORDER BY s.starts_at DESC, a.id DESC",
                Map.of("id", userId), APPOINTMENT_MAPPER);
    }
    public List<AppointmentDto> providerAppointments(long providerId) {
        return jdbc.query(APPOINTMENT_SELECT + " WHERE a.provider_id = :id ORDER BY s.starts_at DESC, a.id DESC",
                Map.of("id", providerId), APPOINTMENT_MAPPER);
    }
    public int cancel(long id, long customerId) {
        return jdbc.update("""
            UPDATE appointments SET status = 'CANCELLED'
            WHERE id = :id AND customer_user_id = :c AND status = 'BOOKED'
            """, Map.of("id", id, "c", customerId));
    }
    public int release(long slotId) {
        return jdbc.update("UPDATE availability_slots SET status = 'AVAILABLE', version = version + 1 WHERE id = :id AND status = 'BOOKED'",
                Map.of("id", slotId));
    }
    public long createSlot(long providerId, long serviceId, LocalDateTime start, LocalDateTime end) {
        var key = new GeneratedKeyHolder();
        jdbc.update("""
            INSERT INTO availability_slots(provider_id, service_id, starts_at, ends_at)
            VALUES (:p, :s, :start, :end)
            """, new MapSqlParameterSource().addValue("p", providerId).addValue("s", serviceId)
                .addValue("start", start).addValue("end", end), key, new String[]{"id"});
        return key.getKey().longValue();
    }
    public int block(long slotId, long providerId, long version) {
        // Soft removal retains slots referenced by cancelled appointment history.
        return jdbc.update("""
            UPDATE availability_slots SET status = 'BLOCKED', version = version + 1
            WHERE id = :id AND provider_id = :p AND version = :v AND status = 'AVAILABLE'
            """, Map.of("id", slotId, "p", providerId, "v", version));
    }
    public void completePastAppointments() {
        jdbc.update("""
            UPDATE appointments SET status = 'COMPLETED' WHERE status = 'BOOKED'
            AND availability_slot_id IN (SELECT id FROM availability_slots WHERE ends_at <= CURRENT_TIMESTAMP)
            """, Map.of());
        jdbc.update("""
            UPDATE availability_slots SET status = 'BLOCKED', version = version + 1
            WHERE status = 'BOOKED' AND ends_at <= CURRENT_TIMESTAMP
            """, Map.of());
    }
}
