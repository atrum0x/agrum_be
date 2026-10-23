package com.atrum.agrum.workType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkTypeRepository extends JpaRepository<WorkType, String> {

    @Query("SELECT wt FROM WorkType wt WHERE wt.estateId = :estateId ORDER BY wt.workType")
    List<WorkType> findAllByEstate(@Param("estateId") String estateId);

    @Query("SELECT wt FROM WorkType wt WHERE wt.workType = :workType AND wt.estateId = :estateId")
    Optional<WorkType> findInEstate(@Param("estateId") String estateId, @Param("workType") String workType);

    @Query("SELECT wt FROM WorkType wt WHERE wt.estateId = :estateId AND wt.workType IN :workTypes")
    List<WorkType> findAllInEstate(@Param("estateId") String estateId, @Param("workTypes") List<String> workTypes);
}
