package com.atrum.agrum.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

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

    private final JdbcTemplate jdbcTemplate;

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

        // Divisions are not used (FSD D-01)
        dropColumn("employees", "division");
        dropColumn("employees", "field");
        if (tableExists("divisions")) {
            jdbcTemplate.execute("DROP TABLE divisions");
            log.info("Schema upgrade: dropped table divisions");
        }

        // One refresh token per user, replaced by refresh_sessions (one per login). Users log in again once
        if (tableExists("refresh_tokens")) {
            jdbcTemplate.execute("DROP TABLE refresh_tokens");
            log.info("Schema upgrade: dropped table refresh_tokens; existing sessions must log in again");
        }
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
