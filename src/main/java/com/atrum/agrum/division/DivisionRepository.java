package com.atrum.agrum.division;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DivisionRepository extends JpaRepository<Division, DivisionId> {

    @Query("SELECT d FROM Division d WHERE d.estateId = :estateId ORDER BY d.divisionCode")
    List<Division> findAllByEstate(@Param("estateId") String estateId);

    @Query("SELECT d FROM Division d WHERE d.divisionCode = :divisionCode AND d.estateId = :estateId")
    Optional<Division> findInEstate(@Param("estateId") String estateId, @Param("divisionCode") String divisionCode);

    @Query("SELECT d FROM Division d WHERE d.estateId = :estateId AND d.divisionCode IN :divisionCodes")
    List<Division> findAllInEstate(@Param("estateId") String estateId,
                                   @Param("divisionCodes") List<String> divisionCodes);

    @Query("SELECT COUNT(d) > 0 FROM Division d WHERE d.estateId = :estateId")
    boolean existsInEstate(@Param("estateId") String estateId);

    // SB-17
    @Query("SELECT COUNT(f) > 0 FROM Field f WHERE f.estateId = :estateId AND f.divisionCode IN :divisionCodes")
    boolean hasFields(@Param("estateId") String estateId, @Param("divisionCodes") List<String> divisionCodes);

    // SB-12. Native, so employees and attendance the user can't see count too
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM employees
                           WHERE default_estate_id = :estateId AND default_division_code IN (:divisionCodes))
                OR EXISTS (SELECT 1 FROM attendance
                           WHERE estate_id = :estateId AND division_code IN (:divisionCodes))
            """, nativeQuery = true)
    boolean isUsed(@Param("estateId") String estateId, @Param("divisionCodes") List<String> divisionCodes);

    // Changing the code of an unused division (SB-12). The key can't change through the entity
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE divisions SET division_code = :newCode WHERE estate_id = :estateId AND division_code = :oldCode",
            nativeQuery = true)
    void changeCode(@Param("estateId") String estateId, @Param("oldCode") String oldCode,
                    @Param("newCode") String newCode);
}
