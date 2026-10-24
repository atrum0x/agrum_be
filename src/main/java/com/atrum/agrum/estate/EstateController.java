package com.atrum.agrum.estate;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import com.atrum.agrum.employee.dto.EmployeeDto;
import com.atrum.agrum.estate.dto.CreateEstateRequest;
import com.atrum.agrum.estate.dto.UpdateEstateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ProjectionGroup("Site Basic Data")
@RestController
@RequestMapping("/api/estates")
public class EstateController {

    private final EstateService estateService;

    public EstateController(EstateService estateService) {
        this.estateService = estateService;
    }

    @Operation(summary = "Create a site")
    @PostMapping
    public ResponseEntity<Estate> createEstate(@RequestBody CreateEstateRequest request) {
        return ResponseEntity.ok(estateService.createEstate(request));
    }

    @Operation(summary = "Change a site's settings: standards, variance, daily limit, status")
    @PutMapping("/{estateId}")
    public ResponseEntity<Estate> updateEstate(
            @PathVariable String estateId,
            @RequestBody UpdateEstateRequest request) {
        return ResponseEntity.ok(estateService.updateEstate(estateId, request));
    }

    @Operation(summary = "List sites. Needed to open Site Basic Data and by most screens that pick a site")
    @GetMapping
    public ResponseEntity<List<Estate>> getAllEstates() {
        return ResponseEntity.ok(estateService.getAllEstates());
    }

    @Operation(summary = "List the users who can see a site")
    @GetMapping("/{estateId}/granted-users")
    public ResponseEntity<List<EmployeeDto>> getGrantedUsers(
            @PathVariable String estateId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(estateService.getGrantedUsers(estateId, search));
    }

    @Operation(summary = "List the users who cannot see a site yet")
    @GetMapping("/{estateId}/unassigned-users")
    public ResponseEntity<List<EmployeeDto>> getUnassignedUsers(
            @PathVariable String estateId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(estateService.getUnassignedUsers(estateId, search));
    }

    @Operation(summary = "Let users see a site's data")
    @PostMapping("/{estateId}/granted-users/grant")
    public ResponseEntity<String> grantUsers(
            @PathVariable String estateId,
            @RequestBody List<String> employeeCodes) {
        estateService.grantUsers(estateId, employeeCodes);
        return ResponseEntity.ok("Users granted to site " + estateId);
    }

    @Operation(summary = "Stop users seeing a site's data")
    @PostMapping("/{estateId}/granted-users/revoke")
    public ResponseEntity<String> revokeUsers(
            @PathVariable String estateId,
            @RequestBody List<String> employeeCodes) {
        estateService.revokeUsers(estateId, employeeCodes);
        return ResponseEntity.ok("Users revoked from site " + estateId);
    }

    // Assign Allowed Estates to User (e.g., PUT /api/admin/estates/user/john_doe)
    @Operation(summary = "Replace the list of sites a user can see")
    @PutMapping("/user/{username}")
    public ResponseEntity<String> assignEstatesToUser(
            @PathVariable String username,
            @RequestBody List<String> estateIds) {

        estateService.assignEstatesToUser(username, estateIds);
        return ResponseEntity.ok("Allowed estates updated successfully for user: " + username);
    }
}