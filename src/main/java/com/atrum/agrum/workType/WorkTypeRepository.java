package com.atrum.agrum.workType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkTypeRepository extends JpaRepository<WorkType, WorkTypeId> {

    @Query("SELECT wt FROM WorkType wt WHERE wt.estateId = :estateId ORDER BY wt.workType")
    List<WorkType> findAllByEstate(@Param("estateId") String estateId);

    @Query("SELECT wt FROM WorkType wt WHERE wt.workType = :workType AND wt.estateId = :estateId")
    Optional<WorkType> findInEstate(@Param("estateId") String estateId, @Param("workType") String workType);

    @Query("SELECT wt FROM WorkType wt WHERE wt.estateId = :estateId AND wt.workType IN :workTypes")
    List<WorkType> findAllInEstate(@Param("estateId") String estateId, @Param("workTypes") List<String> workTypes);

    @Query("SELECT COUNT(wt) > 0 FROM WorkType wt WHERE wt.estateId = :estateId")
    boolean existsInEstate(@Param("estateId") String estateId);

    // SB-12. Native, so attendance at sites the user can't see counts too
    @Query(value = "SELECT EXISTS (SELECT 1 FROM attendance WHERE estate_id = :estateId AND work_type IN (:workTypes))",
            nativeQuery = true)
    boolean isUsedInAttendance(@Param("estateId") String estateId, @Param("workTypes") List<String> workTypes);

    // SB-13: weighings already recorded on the date for attendance with this work type
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM weight_records
                           WHERE estate_id = :estateId AND work_type = :workType AND weigh_date = :date)
            """, nativeQuery = true)
    boolean isWeighedOn(@Param("estateId") String estateId, @Param("workType") String workType,
                        @Param("date") LocalDate date);
}
