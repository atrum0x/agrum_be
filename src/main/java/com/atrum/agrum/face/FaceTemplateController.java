package com.atrum.agrum.face;

import com.atrum.agrum.face.dto.FaceTemplateDto;
import com.atrum.agrum.face.dto.SyncFaceTemplateRequest;
import com.atrum.agrum.face.dto.SyncFaceTemplateResult;
import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@ProjectionGroup("Face Recognition")
@RestController
@RequestMapping("/api/face-templates")
@RequiredArgsConstructor
public class FaceTemplateController {

    private final FaceTemplateService faceTemplateService;

    @Operation(summary = "Download the registered faces of active workers, so the phone can identify workers on the weighing screen. Give to field supervisors who weigh")
    @GetMapping
    public ResponseEntity<List<FaceTemplateDto>> getTemplates() {
        return ResponseEntity.ok(faceTemplateService.getTemplates());
    }

    @Operation(summary = "Send faces registered on the phone; registering a worker again replaces their face. Give to users who register workers' faces")
    @PostMapping("/sync")
    public ResponseEntity<List<SyncFaceTemplateResult>> syncTemplates(@RequestBody SyncFaceTemplateRequest request) {
        return ResponseEntity.ok(faceTemplateService.syncTemplates(request));
    }
}
