package com.atrum.agrum.estate;

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
    public ResponseEntity<Estate> createEstate(@RequestBody Estate estate) {
        return ResponseEntity.ok(estateService.saveEstate(estate));
    }

    @GetMapping
    public ResponseEntity<List<Estate>> getAllEstates() {
        return ResponseEntity.ok(estateService.getAllEstates());
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