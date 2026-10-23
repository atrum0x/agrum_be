package com.atrum.agrum.workType;

import com.atrum.agrum.workType.dto.CreateWorkTypeRequest;
import com.atrum.agrum.workType.dto.UpdateWorkTypeRequest;
import com.atrum.agrum.workType.dto.WorkTypeDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/estates/{estateId}/work-types")
public class WorkTypeController {

    private final WorkTypeService workTypeService;

    public WorkTypeController(WorkTypeService workTypeService) {
        this.workTypeService = workTypeService;
    }

    @GetMapping
    public ResponseEntity<List<WorkTypeDto>> getWorkTypes(@PathVariable String estateId) {
        return ResponseEntity.ok(workTypeService.getWorkTypes(estateId));
    }

    @PostMapping
    public ResponseEntity<WorkTypeDto> createWorkType(
            @PathVariable String estateId,
            @RequestBody CreateWorkTypeRequest request) {
        return ResponseEntity.ok(workTypeService.createWorkType(estateId, request));
    }

    @PutMapping("/{workType}")
    public ResponseEntity<WorkTypeDto> updateWorkType(
            @PathVariable String estateId,
            @PathVariable String workType,
            @RequestBody UpdateWorkTypeRequest request) {
        return ResponseEntity.ok(workTypeService.updateWorkType(estateId, workType, request));
    }

    @PostMapping("/delete")
    public ResponseEntity<String> deleteWorkTypes(
            @PathVariable String estateId,
            @RequestBody List<String> workTypes) {
        workTypeService.deleteWorkTypes(estateId, workTypes);
        return ResponseEntity.ok("Work types deleted from site " + estateId);
    }
}
