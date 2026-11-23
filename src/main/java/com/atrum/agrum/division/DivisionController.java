package com.atrum.agrum.division;

import com.atrum.agrum.division.dto.CreateDivisionRequest;
import com.atrum.agrum.division.dto.DivisionDto;
import com.atrum.agrum.division.dto.UpdateDivisionRequest;
import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ProjectionGroup("Site Basic Data")
@RestController
@RequestMapping("/api/estates/{estateId}/divisions")
public class DivisionController {

    private final DivisionService divisionService;

    public DivisionController(DivisionService divisionService) {
        this.divisionService = divisionService;
    }

    @Operation(summary = "List a site's divisions. Needed by anyone who records attendance or edits sites")
    @GetMapping
    public ResponseEntity<List<DivisionDto>> getDivisions(@PathVariable String estateId) {
        return ResponseEntity.ok(divisionService.getDivisions(estateId));
    }

    @Operation(summary = "Add a division to a site")
    @PostMapping
    public ResponseEntity<DivisionDto> createDivision(
            @PathVariable String estateId,
            @RequestBody CreateDivisionRequest request) {
        return ResponseEntity.ok(divisionService.createDivision(estateId, request));
    }

    @Operation(summary = "Change a division")
    @PutMapping("/{divisionCode}")
    public ResponseEntity<DivisionDto> updateDivision(
            @PathVariable String estateId,
            @PathVariable String divisionCode,
            @RequestBody UpdateDivisionRequest request) {
        return ResponseEntity.ok(divisionService.updateDivision(estateId, divisionCode, request));
    }

    @Operation(summary = "Remove divisions from a site")
    @PostMapping("/delete")
    public ResponseEntity<String> deleteDivisions(
            @PathVariable String estateId,
            @RequestBody List<String> divisionCodes) {
        divisionService.deleteDivisions(estateId, divisionCodes);
        return ResponseEntity.ok("Divisions deleted from site " + estateId);
    }
}
