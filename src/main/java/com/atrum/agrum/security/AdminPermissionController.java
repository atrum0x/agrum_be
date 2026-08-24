package com.atrum.agrum.security;

import com.atrum.agrum.permission.PermissionSet;
import com.atrum.agrum.permission.PermissionSetRepository;
import com.atrum.agrum.projection.Projection;
import com.atrum.agrum.projection.ProjectionRepository;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminPermissionController {

    private final ProjectionRepository projectionRepository;
    private final PermissionSetRepository permissionSetRepository;
    private final AppUserRepository userRepository;

    public AdminPermissionController(ProjectionRepository projectionRepository,
                                     PermissionSetRepository permissionSetRepository,
                                     AppUserRepository userRepository) {
        this.projectionRepository = projectionRepository;
        this.permissionSetRepository = permissionSetRepository;
        this.userRepository = userRepository;
    }

    // 1. List all auto-discovered Projections
    @GetMapping("/projections")
    public ResponseEntity<List<Projection>> getAllProjections() {
        return ResponseEntity.ok(projectionRepository.findAll());
    }

    // 2. Create a new Permission Set
    @PostMapping("/permission-sets")
    public ResponseEntity<PermissionSet> createPermissionSet(@RequestBody PermissionSet permissionSet) {
        return ResponseEntity.ok(permissionSetRepository.save(permissionSet));
    }

    // 3. Add a Projection to a Permission Set
    @PostMapping("/permission-sets/{permissionSetId}/projections/{projectionId}")
    @Transactional
    public ResponseEntity<String> grantProjectionToPermissionSet(
            @PathVariable String permissionSetId,
            @PathVariable String projectionId) {

        PermissionSet ps = permissionSetRepository.findById(permissionSetId)
                .orElseThrow(() -> new RuntimeException("Permission Set not found"));
        Projection proj = projectionRepository.findById(projectionId)
                .orElseThrow(() -> new RuntimeException("Projection not found"));

        ps.addProjection(proj);
        permissionSetRepository.save(ps);

        return ResponseEntity.ok("Projection " + projectionId + " added to Permission Set " + permissionSetId);
    }

    // 4. Assign a Permission Set to a User
    @PostMapping("/users/{username}/permission-sets/{permissionSetId}")
    @Transactional
    public ResponseEntity<String> grantPermissionSetToUser(
            @PathVariable String username,
            @PathVariable String permissionSetId) {

        AppUser user = userRepository.findById(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        PermissionSet ps = permissionSetRepository.findById(permissionSetId)
                .orElseThrow(() -> new RuntimeException("Permission Set not found"));

        user.addPermissionSet(ps);
        userRepository.save(user);

        return ResponseEntity.ok("Permission Set " + permissionSetId + " assigned to User " + username);
    }
}
