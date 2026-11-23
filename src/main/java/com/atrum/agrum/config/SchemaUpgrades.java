package com.atrum.agrum.config;

import com.atrum.agrum.security.PermissionCacheEvictor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.Map;

/**
 * Changes to existing data and tables that ddl-auto: update does not make: it changes column types,
 * but never rewrites values or drops columns and tables. Each step checks first, so it runs once per
 * database. Runs after Hibernate's schema update (it depends on the entity manager factory) and
 * before the app serves requests.
 */
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
class SchemaUpgrades {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrades.class);

    private static final String SITE = "/api/estates/{estateId}";

    /** FSD v2.7: endpoints renamed with Collection Point → Field and the Work Type ID; old id → new id. */
    private static final Map<String, String> RENAMED_ENDPOINTS = Map.of(
            "GET:" + SITE + "/collection-points", "GET:" + SITE + "/fields",
            "POST:" + SITE + "/collection-points", "POST:" + SITE + "/fields",
            "PUT:" + SITE + "/collection-points/{cpCode}", "PUT:" + SITE + "/fields/{fieldCode}",
            "POST:" + SITE + "/collection-points/delete", "POST:" + SITE + "/fields/delete",
            "PUT:" + SITE + "/collection-points/{cpCode}/default", "PUT:" + SITE + "/fields/{fieldCode}/default",
            "PUT:" + SITE + "/work-types/{workType}", "PUT:" + SITE + "/work-types/{workTypeCode}");

    /** New division endpoints, granted once to every set that has the matching field endpoint. */
    private static final Map<String, String> DIVISION_ENDPOINTS = Map.of(
            "GET:" + SITE + "/fields", "GET:" + SITE + "/divisions",
            "POST:" + SITE + "/fields", "POST:" + SITE + "/divisions",
            "PUT:" + SITE + "/fields/{fieldCode}", "PUT:" + SITE + "/divisions/{divisionCode}",
            "POST:" + SITE + "/fields/delete", "POST:" + SITE + "/divisions/delete");

    private final JdbcTemplate jdbcTemplate;
    private final PermissionCacheEvictor permissionCacheEvictor;

    @PostConstruct
    void upgrade() {
        // Employment Category was free text; now the AttendanceCategory enum name. Unknown values become REGULAR
        if (tableExists("employees")) {
            int changed = jdbcTemplate.update("""
                    UPDATE employees
                    SET employee_category = CASE
                        WHEN upper(trim(employee_category)) IN ('REGULAR', 'SEASONAL', 'LENT', 'BORROWED')
                            THEN upper(trim(employee_category))
                        ELSE 'REGULAR' END
                    WHERE employee_category IS NULL
                       OR employee_category NOT IN ('REGULAR', 'SEASONAL', 'LENT', 'BORROWED')
                    """);
            if (changed > 0) {
                log.info("Schema upgrade: mapped {} employee categories to the enum", changed);
            }
        }

        // Columns of the divisions removed in 2026-10 (FSD v2.4). FSD v2.7 divisions are a new table
        dropColumn("employees", "division");
        dropColumn("employees", "field");

        // SB-10: at most one default field per division
        if (tableExists("fields")) {
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_fields_default_per_division
                    ON fields (estate_id, division_code) WHERE is_default
                    """);
        }

        // Correction history JSON written before the Field rename
        if (tableExists("attendance_corrections")) {
            for (String column : new String[]{"old_values", "new_values"}) {
                int changed = jdbcTemplate.update("""
                        UPDATE attendance_corrections
                        SET %1$s = (%1$s - 'cpCode') || jsonb_build_object('fieldCode', %1$s -> 'cpCode')
                        WHERE jsonb_exists(%1$s, 'cpCode')
                        """.formatted(column));
                if (changed > 0) {
                    log.info("Schema upgrade: renamed cpCode to fieldCode in {} attendance_corrections.{}", changed, column);
                }
            }
        }

        moveGrants();

        // One refresh token per user, replaced by refresh_sessions (one per login). Users log in again once
        if (tableExists("refresh_tokens")) {
            jdbcTemplate.execute("DROP TABLE refresh_tokens");
            log.info("Schema upgrade: dropped table refresh_tokens; existing sessions must log in again");
        }
    }

    /**
     * Endpoint discovery deletes the projections of endpoints that no longer exist, with their grants.
     * Before it runs, the grants of renamed endpoints move to the new ids, and the new division
     * endpoints are granted wherever the field ones are, so permission sets keep working.
     */
    private void moveGrants() {
        if (!tableExists("projections") || !tableExists("permission_set_grants")) {
            return;
        }
        int moved = 0;
        for (Map.Entry<String, String> rename : RENAMED_ENDPOINTS.entrySet()) {
            if (projectionExists(rename.getKey())) {
                moved += copyGrants(rename.getKey(), rename.getValue());
            }
        }
        int added = 0;
        for (Map.Entry<String, String> division : DIVISION_ENDPOINTS.entrySet()) {
            // Only on the first start with divisions, so a later revoke stays revoked
            if (!projectionExists(division.getValue())) {
                added += copyGrants(division.getKey(), division.getValue());
            }
        }
        if (moved + added > 0) {
            permissionCacheEvictor.evictAll();
            log.info("Schema upgrade: moved {} grants to renamed endpoints and added {} division grants", moved, added);
        }
    }

    // Creates the target projection if needed (discovery fills in its description) and grants it to
    // every set holding the source
    private int copyGrants(String fromId, String toId) {
        if (!projectionExists(toId)) {
            String[] methodAndPath = toId.split(":", 2);
            jdbcTemplate.update("INSERT INTO projections (projection_id, http_method, url_path) VALUES (?, ?, ?)",
                    toId, methodAndPath[0], methodAndPath[1]);
        }
        return jdbcTemplate.update("""
                INSERT INTO permission_set_grants (permission_set_id, projection_id)
                SELECT g.permission_set_id, ? FROM permission_set_grants g
                WHERE g.projection_id = ?
                  AND NOT EXISTS (SELECT 1 FROM permission_set_grants t
                                  WHERE t.permission_set_id = g.permission_set_id AND t.projection_id = ?)
                """, toId, fromId, toId);
    }

    private boolean projectionExists(String projectionId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM projections WHERE projection_id = ?)", Boolean.class, projectionId));
    }

    private void dropColumn(String table, String column) {
        if (columnType(table, column) != null) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
            log.info("Schema upgrade: dropped {}.{}", table, column);
        }
    }

    private String columnType(String table, String column) {
        return jdbcTemplate.query("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                        """,
                rs -> rs.next() ? rs.getString(1) : null, table, column);
    }

    private boolean tableExists(String table) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM information_schema.tables
                                       WHERE table_schema = current_schema() AND table_name = ?)
                        """,
                Boolean.class, table);
        return Boolean.TRUE.equals(exists);
    }
}
