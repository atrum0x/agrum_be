package com.atrum.agrum.collectionPoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionPointRepository extends JpaRepository<CollectionPoint, String> {

    @Query("""
            SELECT cp FROM CollectionPoint cp
            WHERE cp.estateId = :estateId
              AND (:search = ''
                   OR LOWER(cp.cpCode) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(cp.name) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY cp.isDefault DESC, cp.cpCode
            """)
    List<CollectionPoint> findAllByEstate(@Param("estateId") String estateId, @Param("search") String search);

    @Query("SELECT cp FROM CollectionPoint cp WHERE cp.cpCode = :cpCode AND cp.estateId = :estateId")
    Optional<CollectionPoint> findInEstate(@Param("estateId") String estateId, @Param("cpCode") String cpCode);

    @Query("SELECT cp FROM CollectionPoint cp WHERE cp.estateId = :estateId AND cp.cpCode IN :cpCodes")
    List<CollectionPoint> findAllInEstate(@Param("estateId") String estateId, @Param("cpCodes") List<String> cpCodes);

    @Query("SELECT COUNT(cp) > 0 FROM CollectionPoint cp WHERE cp.estateId = :estateId")
    boolean existsInEstate(@Param("estateId") String estateId);

    @Query("SELECT e.employeeCode FROM Employee e WHERE e.defaultCollectionPoint.cpCode IN :cpCodes ORDER BY e.employeeCode")
    List<String> findEmployeesUsingAsDefault(@Param("cpCodes") List<String> cpCodes);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CollectionPoint cp SET cp.isDefault = false WHERE cp.estateId = :estateId AND cp.cpCode <> :cpCode")
    void clearDefaultExcept(@Param("estateId") String estateId, @Param("cpCode") String cpCode);
}
