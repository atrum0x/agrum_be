package com.atrum.agrum.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Schema changes that must happen before Hibernate's ddl-auto update, because Hibernate builds on
 * them (SchemaUpgrades runs after Hibernate). Each step checks first, so it runs once per database;
 * on a new database the tables don't exist yet and Hibernate creates them correctly.
 */
@Component("schemaPreUpgrades")
class SchemaPreUpgrades {

    private static final Logger log = LoggerFactory.getLogger(SchemaPreUpgrades.class);

    /** The division every existing field, attendance and weighing goes into (FSD v2.7). */
    static final String MAIN_DIVISION = "MAIN";
    private static final String MAIN_DIVISION_DESCRIPTION = "Main Division";

    private static final List<String> RECORD_TABLES = List.of("attendance", "weight_records", "daily_weight_summaries");

    private final JdbcTemplate jdbcTemplate;

    SchemaPreUpgrades(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        upgrade();
    }

    private void upgrade() {
        // Work types and collection points were unique across all sites; FSD SB-09 / SB-11 make them
        // unique within a site. The key becomes (estate_id, code); the foreign keys pointing at the old
        // key are dropped, and Hibernate adds the (estate_id, code) ones
        if (columnExists("work_types", "work_type")) {
            changePrimaryKey("work_types", List.of("estate_id", "work_type"));
        }
        if (tableExists("collection_points")) {
            changePrimaryKey("collection_points", List.of("estate_id", "cp_code"));
        }

        // FSD v2.7: Collection Point is renamed Field, and fields belong to divisions
        renameTable("collection_points", "fields");
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'collection_points_pkey')", Boolean.class))) {
            jdbcTemplate.execute("ALTER TABLE fields RENAME CONSTRAINT collection_points_pkey TO fields_pkey");
        }
        renameColumn("fields", "cp_code", "field_code");
        renameColumn("employees", "default_collection_point_code", "default_field_code");
        for (String table : RECORD_TABLES) {
            renameColumn(table, "collection_point_code", "field_code");
        }

        // FSD v2.7 D-30: the work type's code is its Work Type ID; the name becomes a separate column
        renameColumn("work_types", "work_type", "work_type_code");
        for (String table : RECORD_TABLES) {
            renameColumn(table, "work_type", "work_type_code");
        }
        if (tableExists("work_types") && !columnExists("work_types", "name")) {
            // A readable name (PLUCKING → Plucking), since a used work type can't be renamed later (SB-12).
            // Where two IDs of a site would get the same name (PLUCKING and Plucking), both keep their ID
            jdbcTemplate.execute("ALTER TABLE work_types ADD COLUMN name VARCHAR(50)");
            int named = jdbcTemplate.update("""
                    UPDATE work_types w SET name = CASE WHEN r.clashes > 1 THEN w.work_type_code ELSE r.readable END
                    FROM (SELECT estate_id, work_type_code,
                                 LEFT(INITCAP(REPLACE(work_type_code, '_', ' ')), 50) AS readable,
                                 COUNT(*) OVER (PARTITION BY estate_id,
                                                LOWER(LEFT(INITCAP(REPLACE(work_type_code, '_', ' ')), 50))) AS clashes
                          FROM work_types) r
                    WHERE r.estate_id = w.estate_id AND r.work_type_code = w.work_type_code
                    """);
            jdbcTemplate.execute("ALTER TABLE work_types ALTER COLUMN name SET NOT NULL");
            log.info("Schema upgrade: work_types.name filled from the Work Type ID for {} rows", named);
        }

        addDivisions();
    }

    /**
     * FSD v2.7 D-01: Site → Division → Field. Each existing site gets the division MAIN, and every
     * field, attendance, weighing, summary row and employee default goes into it. Admins add the
     * real divisions and move the fields afterwards.
     */
    private void addDivisions() {
        // The divisions table removed in 2026-10 had another shape (FSD v2.4 D-01); nothing uses it
        if (tableExists("divisions") && !columnExists("divisions", "division_code")) {
            jdbcTemplate.execute("DROP TABLE divisions CASCADE");
            log.info("Schema upgrade: dropped the old divisions table");
        }
        // A database from before divisions has fields without one. A new database has no fields yet,
        // and Hibernate creates everything
        if (!tableExists("fields") || columnExists("fields", "division_code") && !needsDivisionBackfill()) {
            return;
        }

        if (!tableExists("divisions")) {
            jdbcTemplate.execute("""
                    CREATE TABLE divisions (
                        estate_id     VARCHAR(255) NOT NULL,
                        division_code VARCHAR(30)  NOT NULL,
                        description   VARCHAR(50)  NOT NULL,
                        PRIMARY KEY (estate_id, division_code)
                    )
                    """);
        }
        int divisions = jdbcTemplate.update("""
                INSERT INTO divisions (estate_id, division_code, description)
                SELECT e.id, ?, ? FROM estates e
                WHERE NOT EXISTS (SELECT 1 FROM divisions d WHERE d.estate_id = e.id)
                """, MAIN_DIVISION, MAIN_DIVISION_DESCRIPTION);

        // Fields: the FSD has one Field Description, which takes the old name (the label on screen)
        if (!columnExists("fields", "division_code")) {
            jdbcTemplate.execute("ALTER TABLE fields ADD COLUMN division_code VARCHAR(30)");
            jdbcTemplate.update("UPDATE fields SET division_code = ?", MAIN_DIVISION);
            jdbcTemplate.execute("ALTER TABLE fields ALTER COLUMN division_code SET NOT NULL");
        }
        if (columnExists("fields", "name")) {
            jdbcTemplate.execute("""
                    UPDATE fields
                    SET description = LEFT(COALESCE(NULLIF(TRIM(name), ''), NULLIF(TRIM(description), ''), field_code), 50)
                    """);
            jdbcTemplate.execute("ALTER TABLE fields DROP COLUMN name");
        }
        jdbcTemplate.execute("UPDATE fields SET description = field_code WHERE description IS NULL");

        // Records take the division of their field (all MAIN at this point)
        for (String table : RECORD_TABLES) {
            if (!tableExists(table) || columnExists(table, "division_code")) {
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN division_code VARCHAR(30)");
            int rows = jdbcTemplate.update("""
                    UPDATE %s r SET division_code = COALESCE(
                        (SELECT f.division_code FROM fields f WHERE f.estate_id = r.estate_id AND f.field_code = r.field_code),
                        ?)
                    """.formatted(table), MAIN_DIVISION);
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN division_code SET NOT NULL");
            log.info("Schema upgrade: {} rows of {} put in their field's division", rows, table);
        }
        if (tableExists("employees") && !columnExists("employees", "default_division_code")) {
            jdbcTemplate.execute("ALTER TABLE employees ADD COLUMN default_division_code VARCHAR(30)");
            jdbcTemplate.update("""
                    UPDATE employees e SET default_division_code = f.division_code
                    FROM fields f
                    WHERE f.estate_id = e.default_estate_id AND f.field_code = e.default_field_code
                    """);
        }
        log.info("Schema upgrade: added divisions; {} sites got division {}", divisions, MAIN_DIVISION);
    }

    // True while an earlier, interrupted run left a table without its division column
    private boolean needsDivisionBackfill() {
        return columnExists("fields", "name")
                || RECORD_TABLES.stream().anyMatch(t -> tableExists(t) && !columnExists(t, "division_code"))
                || tableExists("employees") && !columnExists("employees", "default_division_code");
    }

    private void changePrimaryKey(String table, List<String> columns) {
        if (!tableExists(table)) {
            return;
        }
        List<String> current = jdbcTemplate.queryForList("""
                SELECT a.attname FROM pg_index i
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                WHERE i.indrelid = to_regclass(?) AND i.indisprimary
                ORDER BY a.attname
                """, String.class, table);
        if (current.equals(columns.stream().sorted().toList())) {
            return;
        }

        List<String[]> foreignKeys = jdbcTemplate.query("""
                        SELECT conrelid::regclass::text, conname FROM pg_constraint
                        WHERE contype = 'f' AND confrelid = to_regclass(?)
                        """,
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, table);
        for (String[] foreignKey : foreignKeys) {
            jdbcTemplate.execute("ALTER TABLE " + foreignKey[0] + " DROP CONSTRAINT \"" + foreignKey[1] + "\"");
        }
        List<String> primaryKey = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE contype = 'p' AND conrelid = to_regclass(?)", String.class, table);
        for (String name : primaryKey) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + name + "\"");
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN estate_id SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD PRIMARY KEY (" + String.join(", ", columns) + ")");
        log.info("Schema upgrade: {} key is now ({}); dropped {} foreign keys for Hibernate to recreate",
                table, String.join(", ", columns), foreignKeys.size());
    }

    // Postgres keeps the keys, foreign keys and indexes on a renamed table or column
    private void renameTable(String from, String to) {
        if (tableExists(from) && !tableExists(to)) {
            jdbcTemplate.execute("ALTER TABLE " + from + " RENAME TO " + to);
            log.info("Schema upgrade: renamed table {} to {}", from, to);
        }
    }

    private void renameColumn(String table, String from, String to) {
        if (columnExists(table, from) && !columnExists(table, to)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " RENAME COLUMN " + from + " TO " + to);
            log.info("Schema upgrade: renamed {}.{} to {}", table, from, to);
        }
    }

    private boolean tableExists(String table) {
        return jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table) == Boolean.TRUE;
    }

    private boolean columnExists(String table, String column) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM information_schema.columns
                               WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?)
                """, Boolean.class, table, column));
    }

    /** Makes the entity manager factory, and so Hibernate's schema update, wait for these steps. */
    @Configuration
    static class RunBeforeHibernate extends EntityManagerFactoryDependsOnPostProcessor {
        RunBeforeHibernate() {
            super("schemaPreUpgrades");
        }
    }
}
