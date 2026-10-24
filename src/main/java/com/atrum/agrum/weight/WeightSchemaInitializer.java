package com.atrum.agrum.weight;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DDL that ddl-auto cannot create from the entities.
 */
@Component
@RequiredArgsConstructor
class WeightSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    // Hibernate has created the tables by now
    @EventListener(ApplicationReadyEvent.class)
    void createIndexes() {
        // WH-05: one pending change per weighing
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_wcr_one_pending
                ON weight_change_requests (weight_id)
                WHERE request_status = 'PENDING_AUTHORIZATION'
                """);
    }
}
