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

    private final JdbcTemplate jdbcTemplate;

    SchemaPreUpgrades(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        upgrade();
    }

    private void upgrade() {
        // Work types and collection points were unique across all sites; FSD SB-09 / SB-11 make them
        // unique within a site. The key becomes (estate_id, code); the foreign keys pointing at the old
        // key are dropped, and Hibernate adds the (estate_id, code) ones
        changePrimaryKey("work_types", List.of("estate_id", "work_type"));
        changePrimaryKey("collection_points", List.of("estate_id", "cp_code"));
    }

    private void changePrimaryKey(String table, List<String> columns) {
        if (jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table) != Boolean.TRUE) {
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

    /** Makes the entity manager factory, and so Hibernate's schema update, wait for these steps. */
    @Configuration
    static class RunBeforeHibernate extends EntityManagerFactoryDependsOnPostProcessor {
        RunBeforeHibernate() {
            super("schemaPreUpgrades");
        }
    }
}
