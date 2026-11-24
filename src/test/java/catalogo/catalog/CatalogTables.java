package catalogo.catalog;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

// Lee y limpia las tablas de la copia local con SQL directo para comprobar la base y reiniciar luego de un test para la integridad del mismo y de los siguientes.
public final class CatalogTables {

    private final JdbcTemplate jdbcTemplate;

    public CatalogTables(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Deja la copia vacía y el estado como lo crea la migración.
    public void reset() {
        jdbcTemplate.update("DELETE FROM weekly_schedules");
        jdbcTemplate.update("DELETE FROM professionals");
        jdbcTemplate.update("DELETE FROM professional_categories");
        jdbcTemplate.update("UPDATE catalog_sync_state SET local_version = NULL, last_sync_at = NULL, "
                + "last_successful_sync_at = NULL, last_sync_type = NULL, last_sync_result = NULL, "
                + "last_error_type = NULL");
    }

    public void insertCategory(long id, String name, String description, boolean enabled) {
        jdbcTemplate.update("INSERT INTO professional_categories (id, name, description, enabled) VALUES (?, ?, ?, ?)",
                id, name, description, enabled);
    }

    public void insertProfessional(long id, long categoryId, String firstName, String lastName, boolean enabled) {
        jdbcTemplate.update("INSERT INTO professionals (id, category_id, first_name, last_name, enabled) "
                + "VALUES (?, ?, ?, ?, ?)", id, categoryId, firstName, lastName, enabled);
    }

    public void insertWeeklySchedule(long id, long professionalId, DayOfWeek dayOfWeek, LocalTime startTime,
                                     LocalTime endTime, int slotDurationMinutes, boolean enabled) {
        jdbcTemplate.update("INSERT INTO weekly_schedules (id, professional_id, day_of_week, start_time, end_time, "
                        + "slot_duration_minutes, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, professionalId, dayOfWeek.name(), startTime, endTime, slotDurationMinutes, enabled);
    }

    public void initializeAt(long version) {
        jdbcTemplate.update("UPDATE catalog_sync_state SET local_version = ?", version);
    }

    public List<Map<String, Object>> categories() {
        return jdbcTemplate.queryForList("SELECT * FROM professional_categories ORDER BY id");
    }

    public List<Map<String, Object>> professionals() {
        return jdbcTemplate.queryForList("SELECT * FROM professionals ORDER BY id");
    }

    public List<Map<String, Object>> weeklySchedules() {
        return jdbcTemplate.queryForList("SELECT * FROM weekly_schedules ORDER BY id");
    }

    public List<Long> ids(String table) {
        return jdbcTemplate.queryForList("SELECT id FROM " + table + " ORDER BY id", Long.class);
    }

    public Map<String, Object> syncState() {
        return jdbcTemplate.queryForMap("SELECT * FROM catalog_sync_state");
    }
}
