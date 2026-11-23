package com.atrum.agrum.field;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldRepository extends JpaRepository<Field, FieldId> {

    // A blank divisionCode lists the fields of every division
    @Query("""
            SELECT f FROM Field f
            WHERE f.estateId = :estateId
              AND (:divisionCode = '' OR f.divisionCode = :divisionCode)
              AND (:search = ''
                   OR LOWER(f.fieldCode) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(f.description) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY f.divisionCode, f.isDefault DESC, f.fieldCode
            """)
    List<Field> findAllByEstate(@Param("estateId") String estateId, @Param("divisionCode") String divisionCode,
                                @Param("search") String search);

    @Query("SELECT f FROM Field f WHERE f.fieldCode = :fieldCode AND f.estateId = :estateId")
    Optional<Field> findInEstate(@Param("estateId") String estateId, @Param("fieldCode") String fieldCode);

    @Query("SELECT f FROM Field f WHERE f.estateId = :estateId AND f.fieldCode IN :fieldCodes")
    List<Field> findAllInEstate(@Param("estateId") String estateId, @Param("fieldCodes") List<String> fieldCodes);

    @Query("SELECT COUNT(f) > 0 FROM Field f WHERE f.estateId = :estateId")
    boolean existsInEstate(@Param("estateId") String estateId);

    @Query("""
            SELECT e.employeeCode FROM Employee e
            WHERE e.defaultEstate.id = :estateId AND e.defaultFieldCode IN :fieldCodes
            ORDER BY e.employeeCode
            """)
    List<String> findEmployeesUsingAsDefault(@Param("estateId") String estateId,
                                             @Param("fieldCodes") List<String> fieldCodes);

    // SB-12. Native, so attendance at sites the user can't see counts too
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM attendance
                           WHERE estate_id = :estateId AND field_code IN (:fieldCodes))
            """, nativeQuery = true)
    boolean isUsedInAttendance(@Param("estateId") String estateId, @Param("fieldCodes") List<String> fieldCodes);

    // SB-10: the other fields of the division stop being the default
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Field f SET f.isDefault = false
            WHERE f.estateId = :estateId AND f.divisionCode = :divisionCode AND f.fieldCode <> :fieldCode
            """)
    void clearDefaultExcept(@Param("estateId") String estateId, @Param("divisionCode") String divisionCode,
                            @Param("fieldCode") String fieldCode);

    // A field that moves to another division takes its employees' default division with it (PB-11).
    // Native, so employees the user can't see move too
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE employees SET default_division_code = :divisionCode
            WHERE default_estate_id = :estateId AND default_field_code = :fieldCode
            """, nativeQuery = true)
    void moveEmployeeDefaults(@Param("estateId") String estateId, @Param("fieldCode") String fieldCode,
                              @Param("divisionCode") String divisionCode);

    // Changing the code of an unused field (SB-12). The key can't change through the entity
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE fields SET field_code = :newCode WHERE estate_id = :estateId AND field_code = :oldCode",
            nativeQuery = true)
    void changeCode(@Param("estateId") String estateId, @Param("oldCode") String oldCode,
                    @Param("newCode") String newCode);
}
