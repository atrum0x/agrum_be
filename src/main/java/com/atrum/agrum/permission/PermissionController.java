package com.atrum.agrum.permission;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import com.atrum.agrum.permission.dto.CreatePermissionSetRequest;
import com.atrum.agrum.permission.dto.PermissionSetDto;
import com.atrum.agrum.permission.dto.UpdatePermissionSetRequest;
import com.atrum.agrum.projection.dto.ProjectionDto;
import com.atrum.agrum.projection.Projection;
import com.atrum.agrum.user.dto.AppUserDto;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

import java.util.List;

@ProjectionGroup("Authorization Basic Data")
@RestController
@RequestMapping("/api/permissionSet")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    //List all auto-discovered Projections
    @Operation(summary = "List every endpoint that can be granted")
    @GetMapping("/projections")
    public ResponseEntity<List<Projection>> getAllProjections() {
        return ResponseEntity.ok(permissionService.getAllProjections());
    }

    //Create a new Permission Set
    @Operation(summary = "Create a permission set")
    @PostMapping("/permission-sets")
    public ResponseEntity<PermissionSetDto> createPermissionSet(@RequestBody CreatePermissionSetRequest request) {
        return ResponseEntity.ok(permissionService.createPermissionSet(request));
    }

    //Update a Permission Set's description
    @Operation(summary = "Change a permission set's description")
    @PutMapping("{permissionSetId}")
    public ResponseEntity<PermissionSetDto> updatePermissionSet(
            @PathVariable String permissionSetId,
            @RequestBody UpdatePermissionSetRequest request) {
        return ResponseEntity.ok(permissionService.updatePermissionSet(permissionSetId, request));
    }


    @Setter
    @Getter
    public static class ProjectionsRequest {
        private List<String> projectionIds;
    }

    //Add a Projection to a Permission Set
    @Operation(summary = "Add endpoints to a permission set")
    @PostMapping("{permissionSetId}/grantProjections")
    public ResponseEntity<String> grantProjectionsToPermissionSet(
            @PathVariable("permissionSetId") String permissionSetId,
            @RequestBody ProjectionsRequest request) {

        permissionService.grantProjectionsToPermissionSet(permissionSetId, request.getProjectionIds());
        return ResponseEntity.ok(request.getProjectionIds().size() + " projection(s) granted to Permission Set " + permissionSetId);
    }

    @Operation(summary = "Remove endpoints from a permission set")
    @DeleteMapping("{permissionSetId}/revokeProjections")
    public ResponseEntity<String> revokeProjectionsFromPermissionSet(
            @PathVariable("permissionSetId") String permissionSetId,
            @RequestBody ProjectionsRequest request) {

        permissionService.revokeProjectionsFromPermissionSet(permissionSetId, request.getProjectionIds());
        return ResponseEntity.ok(request.getProjectionIds().size() + " projection(s) revoked from Permission Set " + permissionSetId);
    }

    @Operation(summary = "Give a permission set to users")
    @PostMapping("{permissionSetId}/grantUsers")
    public ResponseEntity<String> grantPermissionSetToUser(
            @RequestBody List<String> usernames,
            @PathVariable String permissionSetId) {

        permissionService.grantPermissionSetToUsers(usernames, permissionSetId);
        return ResponseEntity.ok("Permission Set " + permissionSetId + " assigned to Users");
    }

    @Operation(summary = "Take a permission set away from users")
    @PostMapping("{permissionSetId}/revokeUsers")
    public ResponseEntity<String> revokePermissionSetToUser(
            @RequestBody List<String> usernames,
            @PathVariable String permissionSetId
    ) {
        permissionService.revokePermissionSetToUsers(usernames, permissionSetId);
        return ResponseEntity.ok("Permission Set " + permissionSetId + " revoked from Users");
    }

    @Operation(summary = "View the endpoints in a permission set")
    @GetMapping("grantedProjections/{permissionSetId}")
    public ResponseEntity<Page<ProjectionDto>> getGrantedProjections(
            @PathVariable String permissionSetId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = {"groupName", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(permissionService.grantedProjections(permissionSetId, search, pageable));
    }

    @Operation(summary = "List endpoints not in a permission set yet")
    @GetMapping("revokedProjections/{permissionSetId}")
    public ResponseEntity<Page<ProjectionDto>> getUnassignedProjections(
            @PathVariable String permissionSetId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = {"groupName", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(permissionService.getUnassignedProjections(permissionSetId, search, pageable));
    }

    @Operation(summary = "List permission sets. Needed to open Permission Sets")
    @GetMapping("")
    public ResponseEntity<List<PermissionSetDto>> getAllPermissionSets(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(permissionService.allPermissions(search));
    }

    @Operation(summary = "View the users who have a permission set")
    @GetMapping("grantedUsers/{permissionID}")
    public ResponseEntity<List<AppUserDto>> getGrantedAppUsers(@PathVariable(required = true) String permissionID, @RequestParam(required = false) String search) {
        return ResponseEntity.ok(permissionService.getGrantedUsers(permissionID, search));
    }

    @Operation(summary = "List users who do not have a permission set yet")
    @GetMapping("unassignedUsers/{permissionID}")
    public ResponseEntity<List<AppUserDto>> getUnassignedAppUsers(@PathVariable(required = true) String permissionID, @RequestParam(required = false) String search) {
        return ResponseEntity.ok(permissionService.getUnassignedUsers(permissionID, search));
    }
}