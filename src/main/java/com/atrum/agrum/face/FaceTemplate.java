package com.atrum.agrum.face;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One worker's face for recognition on the weighing screen: the mean ArcFace embedding of the
 * poses captured on a phone. Registering again replaces it. No photo is sent to the server.
 */
@Entity
@Table(name = "face_templates")
@Getter
@Setter
@NoArgsConstructor
public class FaceTemplate {

    @Id
    @Column(name = "employee_code", nullable = false, updatable = false, length = 50)
    private String employeeCode;

    // Which model made the embedding; a phone only matches templates from its own model
    @Column(name = "model", nullable = false, length = 30)
    private String model;

    // 512 float32 values, little-endian
    @Column(name = "embedding", nullable = false)
    private byte[] embedding;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    // When the poses were captured on the phone; the newest registration wins
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "registered_by", nullable = false, length = 100)
    private String registeredBy;

    @UpdateTimestamp
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
