package com.atrum.agrum.projection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface ProjectionRepository extends JpaRepository<Projection, String> {

    // Grants of removed endpoints; the join table has no entity of its own
    @Modifying
    @Query(value = "DELETE FROM permission_set_grants WHERE projection_id IN (:projectionIds)", nativeQuery = true)
    void deleteGrantsOf(@Param("projectionIds") Collection<String> projectionIds);
}