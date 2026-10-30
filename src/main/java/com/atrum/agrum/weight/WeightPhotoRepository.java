package com.atrum.agrum.weight;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WeightPhotoRepository extends JpaRepository<WeightPhoto, UUID> {
}
