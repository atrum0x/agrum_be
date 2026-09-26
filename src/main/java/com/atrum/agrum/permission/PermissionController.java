package com.atrum.agrum.permission;

import com.atrum.agrum.permission.dto.PermissionSetDto;
import com.atrum.agrum.permission.dto.ProjectionDto;
import com.atrum.agrum.projection.Projection;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/permissionSet")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    // 1. List all auto-discovered Projections
    @GetMapping("/projections")
    public ResponseEntity<List<Projection>> getAllProjections() {
        return ResponseEntity.ok(permissionService.getAllProjections());
    }

    // 2. Create a new Permission Set
    @PostMapping("/permission-sets")
    public ResponseEntity<PermissionSet> createPermissionSet(@RequestBody PermissionSet permissionSet) {
        return ResponseEntity.ok(permissionService.createPermissionSet(permissionSet));
    }


    @Setter
    @Getter
    public static class ProjectionRequest {
        private String projectionId;
    }
    // 3. Add a Projection to a Permission Set
    @PostMapping("/permission-sets/{permissionSetId}/projections")
    public ResponseEntity<String> grantProjectionToPermissionSet(
            @PathVariable("permissionSetId") String permissionSetId,
            @RequestBody ProjectionRequest request) {

        permissionService.grantProjectionToPermissionSet(permissionSetId, request.getProjectionId());
        return ResponseEntity.ok("Projection " + request.getProjectionId() + " added to Permission Set " + permissionSetId);
    }

    // 4. Assign a Permission Set to a User
    @PostMapping("/users/{username}/permission-sets/{permissionSetId}")
    public ResponseEntity<String> grantPermissionSetToUser(
            @PathVariable String username,
            @PathVariable String permissionSetId) {

        permissionService.grantPermissionSetToUser(username, permissionSetId);
        return ResponseEntity.ok("Permission Set " + permissionSetId + " assigned to User " + username);
    }

    @GetMapping("grantedProjections/{permissionSetId}")
    public ResponseEntity<Set<ProjectionDto>> getGrantedProjections(@PathVariable String permissionSetId,
                                                                    @RequestParam(required = false) String search,
                                                                    @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(permissionService.grantedProjections(permissionSetId));
    }

    @GetMapping("")
    public ResponseEntity<List<PermissionSetDto>> getAllPermissionSets(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(permissionService.allPermissions(search));
    }
}