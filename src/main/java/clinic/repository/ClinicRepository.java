package clinic.repository;

import clinic.dto.AvailabilitySlotDto;
import clinic.dto.ProviderDto;
import clinic.dto.ServiceDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class ClinicRepository {

    private static final String SLOT_FROM_AND_WHERE = """
            FROM availability_slots slot
            JOIN providers provider ON provider.id = slot.provider_id
            JOIN users provider_user ON provider_user.id = provider.user_id
            JOIN services service ON service.id = slot.service_id
            WHERE slot.status = 'AVAILABLE'
              AND slot.starts_at > CURRENT_TIMESTAMP
              AND provider.active = TRUE AND service.active = TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ClinicRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProviderDto> findActiveProviders() {
        return jdbc.query("""
                SELECT provider.id, provider_user.full_name, provider.specialty
                FROM providers provider
                JOIN users provider_user ON provider_user.id = provider.user_id
                WHERE provider.active = TRUE
                ORDER BY provider_user.full_name
                """, new MapSqlParameterSource(), (rs, rowNum) -> new ProviderDto(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("specialty")
        ));
    }

    public List<ServiceDto> findActiveServices() {
        return jdbc.query("""
                SELECT id, provider_id, name, description, duration_minutes, price
                FROM services
                WHERE active = TRUE
                ORDER BY name
                """, new MapSqlParameterSource(), (rs, rowNum) -> new ServiceDto(
                rs.getLong("id"),
                rs.getLong("provider_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("duration_minutes"),
                rs.getBigDecimal("price")
        ));
    }

    public List<AvailabilitySlotDto> findAvailableSlots(
            Long providerId,
            Long serviceId,
            LocalDate date,
            int limit,
            long offset
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        String filters = slotFilters(providerId, serviceId, date, parameters);

        return jdbc.query("""
                SELECT slot.id,
                       slot.provider_id,
                       provider_user.full_name AS provider_name,
                       provider.specialty,
                       slot.service_id,
                       service.name AS service_name,
                       slot.starts_at,
                       slot.ends_at
                """ + SLOT_FROM_AND_WHERE + filters + """
                ORDER BY slot.starts_at, provider_user.full_name, slot.id
                LIMIT :limit OFFSET :offset
                """, parameters, (rs, rowNum) -> new AvailabilitySlotDto(
                rs.getLong("id"),
                rs.getLong("provider_id"),
                rs.getString("provider_name"),
                rs.getString("specialty"),
                rs.getLong("service_id"),
                rs.getString("service_name"),
                rs.getTimestamp("starts_at").toLocalDateTime(),
                rs.getTimestamp("ends_at").toLocalDateTime()
        ));
    }

    public long countAvailableSlots(Long providerId, Long serviceId, LocalDate date) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String filters = slotFilters(providerId, serviceId, date, parameters);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) " + SLOT_FROM_AND_WHERE + filters,
                parameters,
                Long.class
        );
        return count == null ? 0 : count;
    }

    private String slotFilters(
            Long providerId,
            Long serviceId,
            LocalDate date,
            MapSqlParameterSource parameters
    ) {
        StringBuilder filters = new StringBuilder();
        if (providerId != null) {
            filters.append(" AND slot.provider_id = :providerId\n");
            parameters.addValue("providerId", providerId);
        }
        if (serviceId != null) {
            filters.append(" AND slot.service_id = :serviceId\n");
            parameters.addValue("serviceId", serviceId);
        }
        if (date != null) {
            filters.append(" AND CAST(slot.starts_at AS DATE) = :slotDate\n");
            parameters.addValue("slotDate", date);
        }
        return filters.toString();
    }
}
