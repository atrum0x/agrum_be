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

    @Query("SELECT wt FROM WorkType wt WHERE wt.estateId = :estateId ORDER BY wt.workTypeCode")
    List<WorkType> findAllByEstate(@Param("estateId") String estateId);

    @Query("SELECT wt FROM WorkType wt WHERE wt.workTypeCode = :workTypeCode AND wt.estateId = :estateId")
    Optional<WorkType> findInEstate(@Param("estateId") String estateId, @Param("workTypeCode") String workTypeCode);

    @Query("SELECT wt FROM WorkType wt WHERE wt.estateId = :estateId AND wt.workTypeCode IN :workTypeCodes")
    List<WorkType> findAllInEstate(@Param("estateId") String estateId,
                                   @Param("workTypeCodes") List<String> workTypeCodes);

    @Query("SELECT COUNT(wt) > 0 FROM WorkType wt WHERE wt.estateId = :estateId")
    boolean existsInEstate(@Param("estateId") String estateId);

    // SB-11: the name is unique within the site, ignoring case
    @Query("""
            SELECT COUNT(wt) > 0 FROM WorkType wt
            WHERE wt.estateId = :estateId AND LOWER(wt.name) = LOWER(:name) AND wt.workTypeCode <> :exceptCode
            """)
    boolean nameExists(@Param("estateId") String estateId, @Param("name") String name,
                       @Param("exceptCode") String exceptCode);

    // SB-12. Native, so attendance at sites the user can't see counts too
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM attendance
                           WHERE estate_id = :estateId AND work_type_code IN (:workTypeCodes))
            """, nativeQuery = true)
    boolean isUsedInAttendance(@Param("estateId") String estateId, @Param("workTypeCodes") List<String> workTypeCodes);

    // SB-13: weighings already recorded on the date for attendance with this work type
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM weight_records
                           WHERE estate_id = :estateId AND work_type_code = :workTypeCode AND weigh_date = :date)
            """, nativeQuery = true)
    boolean isWeighedOn(@Param("estateId") String estateId, @Param("workTypeCode") String workTypeCode,
                        @Param("date") LocalDate date);
}
