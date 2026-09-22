package com.atrum.agrum.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, String> {
    @Query("SELECT e.id FROM AppUser u JOIN u.allowedEstates e WHERE u.username = :username")
    List<String> findAllowedEstateIdsByUsername(@Param("username") String username);
}
