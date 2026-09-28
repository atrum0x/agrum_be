package com.atrum.agrum.permission;

import com.atrum.agrum.projection.Projection;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionSetRepository extends JpaRepository<PermissionSet, String> {

    /**
     * Fetches all projection URL patterns granted to the user for the given HTTP method.
     * Returning the list of patterns allows Spring's PathPatternParser to accurately match
     * REST parameters like '/api/users/{id}' against incoming requests like '/api/users/42'.
     */
    @Cacheable(value = "userPermissions", key = "#username + '_' + #httpMethod")
    @Query("""
                SELECT DISTINCT p.urlPath 
                FROM AppUser u 
                JOIN u.permissionSets ps 
                JOIN ps.projections p 
                WHERE u.username = :username 
                  AND p.httpMethod = :httpMethod
            """)
    List<String> findAllowedPathPatternsByUsernameAndHttpMethod(
            @Param("username") String username,
            @Param("httpMethod") String httpMethod
    );

    List<PermissionSet> findByIdContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String id, String description);

    @Query("""
                SELECT p FROM Projection p
                WHERE NOT EXISTS (
                    SELECT 1 FROM p.permissionSets ps
                    WHERE ps.id = :permissionSetId
                )
                AND (
                    :search = ''
                    OR LOWER(p.id) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))
                )
            """)
    Page<Projection> findUnassignedProjections(
            @Param("permissionSetId") String permissionSetId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
                SELECT p FROM Projection p
                JOIN p.permissionSets ps
                WHERE ps.id = :permissionSetId
                AND (
                    :search = ''
                    OR LOWER(p.id) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))
                )
            """)
    Page<Projection> findGrantedProjections(
            @Param("permissionSetId") String permissionSetId,
            @Param("search") String search,
            Pageable pageable
    );
}
