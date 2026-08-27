package com.atrum.agrum.projection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectionRepository extends JpaRepository<Projection, String> {
}