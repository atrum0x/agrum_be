package com.atrum.agrum.permission;

import com.atrum.agrum.permission.dto.PermissionSetDto;
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

@RestController
@RequestMapping("/api/permissionSet")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    //List all auto-discovered Projections
    @GetMapping("/projections")
    public ResponseEntity<List<Projection>> getAllProjections() {
        return ResponseEntity.ok(permissionService.getAllProjections());
    }

    //Create a new Permission Set
    @PostMapping("/permission-sets")
    public ResponseEntity<PermissionSet> createPermissionSet(@RequestBody PermissionSet permissionSet) {
        return ResponseEntity.ok(permissionService.createPermissionSet(permissionSet));
    }


    @Setter
    @Getter
    public static class ProjectionsRequest {
        private List<String> projectionIds;
    }

    //Add a Projection to a Permission Set
    @PostMapping("{permissionSetId}/grantProjections")
    public ResponseEntity<String> grantProjectionsToPermissionSet(
            @PathVariable("permissionSetId") String permissionSetId,
            @RequestBody ProjectionsRequest request) {

        permissionService.grantProjectionsToPermissionSet(permissionSetId, request.getProjectionIds());
        return ResponseEntity.ok(request.getProjectionIds().size() + " projection(s) granted to Permission Set " + permissionSetId);
    }

    @DeleteMapping("{permissionSetId}/revokeProjections")
    public ResponseEntity<String> revokeProjectionsFromPermissionSet(
            @PathVariable("permissionSetId") String permissionSetId,
            @RequestBody ProjectionsRequest request) {

        permissionService.revokeProjectionsFromPermissionSet(permissionSetId, request.getProjectionIds());
        return ResponseEntity.ok(request.getProjectionIds().size() + " projection(s) revoked from Permission Set " + permissionSetId);
    }

    @PostMapping("{permissionSetId}/grantUsers")
    public ResponseEntity<String> grantPermissionSetToUser(
            @RequestBody List<String> usernames,
            @PathVariable String permissionSetId) {

        permissionService.grantPermissionSetToUsers(usernames, permissionSetId);
        return ResponseEntity.ok("Permission Set " + permissionSetId + " assigned to Users");
    }

    @PostMapping("{permissionSetId}/revokeUsers")
    public ResponseEntity<String> revokePermissionSetToUser(
            @RequestBody List<String> usernames,
            @PathVariable String permissionSetId
    ) {
        permissionService.revokePermissionSetToUsers(usernames, permissionSetId);
        return ResponseEntity.ok("Permission Set " + permissionSetId + " revoked from Users");
    }

    @GetMapping("grantedProjections/{permissionSetId}")
    public ResponseEntity<Page<ProjectionDto>> getGrantedProjections(
            @PathVariable String permissionSetId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(permissionService.grantedProjections(permissionSetId, search, pageable));
    }

    @GetMapping("revokedProjections/{permissionSetId}")
    public ResponseEntity<Page<ProjectionDto>> getUnassignedProjections(
            @PathVariable String permissionSetId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(permissionService.getUnassignedProjections(permissionSetId, search, pageable));
    }

    @GetMapping("")
    public ResponseEntity<List<PermissionSetDto>> getAllPermissionSets(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(permissionService.allPermissions(search));
    }

    @GetMapping("grantedUsers/{permissionID}")
    public ResponseEntity<List<AppUserDto>> getGrantedAppUsers(@PathVariable(required = true) String permissionID, @RequestParam(required = false) String search) {
        return ResponseEntity.ok(permissionService.getGrantedUsers(permissionID, search));
    }

    @GetMapping("unassignedUsers/{permissionID}")
    public ResponseEntity<List<AppUserDto>> getUnassignedAppUsers(@PathVariable(required = true) String permissionID, @RequestParam(required = false) String search) {
        return ResponseEntity.ok(permissionService.getUnassignedUsers(permissionID, search));
    }
}