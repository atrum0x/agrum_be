package com.atrum.agrum.field;

import com.atrum.agrum.field.dto.CreateFieldRequest;
import com.atrum.agrum.field.dto.FieldDto;
import com.atrum.agrum.field.dto.UpdateFieldRequest;
import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ProjectionGroup("Site Basic Data")
@RestController
@RequestMapping("/api/estates/{estateId}/fields")
public class FieldController {

    private final FieldService fieldService;

    public FieldController(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @Operation(summary = "List a site's fields, optionally of one division. Needed by anyone who records attendance or edits sites")
    @GetMapping
    public ResponseEntity<List<FieldDto>> getFields(
            @PathVariable String estateId,
            @RequestParam(required = false) String divisionCode,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(fieldService.getFields(estateId, divisionCode, search));
    }

    @Operation(summary = "Add a field to a site")
    @PostMapping
    public ResponseEntity<FieldDto> createField(
            @PathVariable String estateId,
            @RequestBody CreateFieldRequest request) {
        return ResponseEntity.ok(fieldService.createField(estateId, request));
    }

    @Operation(summary = "Change a field")
    @PutMapping("/{fieldCode}")
    public ResponseEntity<FieldDto> updateField(
            @PathVariable String estateId,
            @PathVariable String fieldCode,
            @RequestBody UpdateFieldRequest request) {
        return ResponseEntity.ok(fieldService.updateField(estateId, fieldCode, request));
    }

    @Operation(summary = "Remove fields from a site")
    @PostMapping("/delete")
    public ResponseEntity<String> deleteFields(
            @PathVariable String estateId,
            @RequestBody List<String> fieldCodes) {
        fieldService.deleteFields(estateId, fieldCodes);
        return ResponseEntity.ok("Fields deleted from site " + estateId);
    }

    @Operation(summary = "Make a field the default of its division")
    @PutMapping("/{fieldCode}/default")
    public ResponseEntity<FieldDto> setDefaultField(
            @PathVariable String estateId,
            @PathVariable String fieldCode) {
        return ResponseEntity.ok(fieldService.setDefault(estateId, fieldCode));
    }
}
