package com.atrum.agrum.weight;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * The face the phone recognised for a weighing, as a small JPEG. A table of its own, so weight
 * searches and the summary job never load the pictures.
 */
@Entity
@Table(name = "weight_photos")
@Getter
@Setter
@NoArgsConstructor
public class WeightPhoto {

    @Id
    @Column(name = "weight_id", nullable = false, updatable = false)
    private UUID weightId;

    @Column(name = "photo", nullable = false)
    private byte[] photo;
}
