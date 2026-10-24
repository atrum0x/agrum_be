package com.atrum.agrum.weight;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Last run of a scheduled job. The daily summary reruns every date changed since
 * lastSuccessAt, and the hourly retry runs only while lastError is set.
 */
@Entity
@Table(name = "job_run_states")
@Getter
@Setter
@NoArgsConstructor
public class JobRunState {

    @Id
    @Column(name = "job_name", length = 50)
    private String jobName;

    // Start time of the last successful run
    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    // Null after a successful run
    @Column(name = "last_error", length = 1000)
    private String lastError;
}
