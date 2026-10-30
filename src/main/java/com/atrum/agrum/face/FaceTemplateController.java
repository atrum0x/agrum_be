package com.atrum.agrum.face;

import com.atrum.agrum.face.dto.FaceRegistrationDto;
import com.atrum.agrum.face.dto.FaceTemplateDto;
import com.atrum.agrum.face.dto.SyncFaceTemplateRequest;
import com.atrum.agrum.face.dto.SyncFaceTemplateResult;
import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(summary = "View whether a worker has a registered face, and when and by whom it was registered (employee page)")
    @GetMapping("/{employeeCode}")
    public ResponseEntity<FaceRegistrationDto> getRegistration(@PathVariable String employeeCode) {
        return faceTemplateService.getRegistration(employeeCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "View a worker's registered face photo, e.g. next to a weighing photo in Weight History")
    @GetMapping(value = "/{employeeCode}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getFacePhoto(@PathVariable String employeeCode) {
        return faceTemplateService.getPhoto(employeeCode)
                .map(photo -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(photo))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Remove a worker's registered face, e.g. one registered to the wrong employee. Give to users who manage face registration")
    @DeleteMapping("/{employeeCode}")
    public ResponseEntity<Void> removeTemplate(@PathVariable String employeeCode) {
        return faceTemplateService.removeTemplate(employeeCode)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Send faces registered on the phone; registering a worker again replaces their face. Give to users who register workers' faces")
    @PostMapping("/sync")
    public ResponseEntity<List<SyncFaceTemplateResult>> syncTemplates(@RequestBody SyncFaceTemplateRequest request) {
        return ResponseEntity.ok(faceTemplateService.syncTemplates(request));
    }
}
