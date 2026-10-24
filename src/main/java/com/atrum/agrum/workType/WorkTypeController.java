package com.atrum.agrum.workType;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import com.atrum.agrum.workType.dto.CreateWorkTypeRequest;
import com.atrum.agrum.workType.dto.UpdateWorkTypeRequest;
import com.atrum.agrum.workType.dto.WorkTypeDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ProjectionGroup("Site Basic Data")
@RestController
@RequestMapping("/api/estates/{estateId}/work-types")
public class WorkTypeController {

    private final WorkTypeService workTypeService;

    public WorkTypeController(WorkTypeService workTypeService) {
        this.workTypeService = workTypeService;
    }

    @Operation(summary = "List a site's work types. Needed by anyone who records attendance or edits sites")
    @GetMapping
    public ResponseEntity<List<WorkTypeDto>> getWorkTypes(@PathVariable String estateId) {
        return ResponseEntity.ok(workTypeService.getWorkTypes(estateId));
    }

    @Operation(summary = "Add a work type to a site")
    @PostMapping
    public ResponseEntity<WorkTypeDto> createWorkType(
            @PathVariable String estateId,
            @RequestBody CreateWorkTypeRequest request) {
        return ResponseEntity.ok(workTypeService.createWorkType(estateId, request));
    }

    @Operation(summary = "Change a work type (e.g. Weighing Required)")
    @PutMapping("/{workType}")
    public ResponseEntity<WorkTypeDto> updateWorkType(
            @PathVariable String estateId,
            @PathVariable String workType,
            @RequestBody UpdateWorkTypeRequest request) {
        return ResponseEntity.ok(workTypeService.updateWorkType(estateId, workType, request));
    }

    @Operation(summary = "Remove work types from a site")
    @PostMapping("/delete")
    public ResponseEntity<String> deleteWorkTypes(
            @PathVariable String estateId,
            @RequestBody List<String> workTypes) {
        workTypeService.deleteWorkTypes(estateId, workTypes);
        return ResponseEntity.ok("Work types deleted from site " + estateId);
    }
}
