package com.atrum.agrum.estate;

import com.atrum.agrum.employee.dto.EmployeeDto;
import com.atrum.agrum.estate.dto.CreateEstateRequest;
import com.atrum.agrum.estate.dto.UpdateEstateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/estates")
public class EstateController {

    private final EstateService estateService;

    public EstateController(EstateService estateService) {
        this.estateService = estateService;
    }

    @PostMapping
    public ResponseEntity<Estate> createEstate(@RequestBody CreateEstateRequest request) {
        return ResponseEntity.ok(estateService.createEstate(request));
    }

    @PutMapping("/{estateId}")
    public ResponseEntity<Estate> updateEstate(
            @PathVariable String estateId,
            @RequestBody UpdateEstateRequest request) {
        return ResponseEntity.ok(estateService.updateEstate(estateId, request));
    }

    @GetMapping
    public ResponseEntity<List<Estate>> getAllEstates() {
        return ResponseEntity.ok(estateService.getAllEstates());
    }

    @GetMapping("/{estateId}/granted-users")
    public ResponseEntity<List<EmployeeDto>> getGrantedUsers(
            @PathVariable String estateId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(estateService.getGrantedUsers(estateId, search));
    }

    @GetMapping("/{estateId}/unassigned-users")
    public ResponseEntity<List<EmployeeDto>> getUnassignedUsers(
            @PathVariable String estateId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(estateService.getUnassignedUsers(estateId, search));
    }

    @PostMapping("/{estateId}/granted-users/grant")
    public ResponseEntity<String> grantUsers(
            @PathVariable String estateId,
            @RequestBody List<String> employeeCodes) {
        estateService.grantUsers(estateId, employeeCodes);
        return ResponseEntity.ok("Users granted to site " + estateId);
    }

    @PostMapping("/{estateId}/granted-users/revoke")
    public ResponseEntity<String> revokeUsers(
            @PathVariable String estateId,
            @RequestBody List<String> employeeCodes) {
        estateService.revokeUsers(estateId, employeeCodes);
        return ResponseEntity.ok("Users revoked from site " + estateId);
    }

    // Assign Allowed Estates to User (e.g., PUT /api/admin/estates/user/john_doe)
    @PutMapping("/user/{username}")
    public ResponseEntity<String> assignEstatesToUser(
            @PathVariable String username,
            @RequestBody List<String> estateIds) {

        estateService.assignEstatesToUser(username, estateIds);
        return ResponseEntity.ok("Allowed estates updated successfully for user: " + username);
    }
}