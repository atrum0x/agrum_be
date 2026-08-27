package com.atrum.agrum.security;

import com.atrum.agrum.permission.PermissionSet;
import com.atrum.agrum.projection.Projection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminPermissionController {

    private final AdminPermissionService adminPermissionService;

    public AdminPermissionController(AdminPermissionService adminPermissionService) {
        this.adminPermissionService = adminPermissionService;
    }

    // 1. List all auto-discovered Projections
    @GetMapping("/projections")
    public ResponseEntity<List<Projection>> getAllProjections() {
        return ResponseEntity.ok(adminPermissionService.getAllProjections());
    }

    // 2. Create a new Permission Set
    @PostMapping("/permission-sets")
    public ResponseEntity<PermissionSet> createPermissionSet(@RequestBody PermissionSet permissionSet) {
        return ResponseEntity.ok(adminPermissionService.createPermissionSet(permissionSet));
    }

    // 3. Add a Projection to a Permission Set
    @PostMapping("/permission-sets/{permissionSetId}/projections/{projectionId}")
    public ResponseEntity<String> grantProjectionToPermissionSet(
            @PathVariable String permissionSetId,
            @PathVariable String projectionId) {

        adminPermissionService.grantProjectionToPermissionSet(permissionSetId, projectionId);
        return ResponseEntity.ok("Projection " + projectionId + " added to Permission Set " + permissionSetId);
    }

    // 4. Assign a Permission Set to a User
    @PostMapping("/users/{username}/permission-sets/{permissionSetId}")
    public ResponseEntity<String> grantPermissionSetToUser(
            @PathVariable String username,
            @PathVariable String permissionSetId) {

        adminPermissionService.grantPermissionSetToUser(username, permissionSetId);
        return ResponseEntity.ok("Permission Set " + permissionSetId + " assigned to User " + username);
    }
}