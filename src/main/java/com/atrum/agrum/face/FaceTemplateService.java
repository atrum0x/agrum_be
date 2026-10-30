package com.atrum.agrum.face;

import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.EmploymentStatus;
import com.atrum.agrum.face.dto.FaceTemplateDto;
import com.atrum.agrum.face.dto.SyncFaceTemplateRequest;
import com.atrum.agrum.face.dto.SyncFaceTemplateResult;
import com.atrum.agrum.user.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Face templates registered on phones. Sync is not @Transactional: each template is saved on its
 * own, so one rejected template does not fail the others.
 */
@Service
@RequiredArgsConstructor
public class FaceTemplateService {

    public static final String MODEL = "arcface_512";
    static final int EMBEDDING_BYTES = 512 * Float.BYTES;

    private static final Logger log = LoggerFactory.getLogger(FaceTemplateService.class);

    private final FaceTemplateRepository faceTemplateRepository;
    private final EmployeeRepository employeeRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FaceTemplateDto> getTemplates() {
        return faceTemplateRepository.findForEmployedOn(EmploymentStatus.ACTIVE, LocalDate.now(clock))
                .stream()
                .map(FaceTemplateService::toDto)
                .toList();
    }

    /** Each template gets its own Sent / Rejected answer, in the order the phone sends them. */
    public List<SyncFaceTemplateResult> syncTemplates(SyncFaceTemplateRequest request) {
        if (request == null || request.records() == null) {
            return List.of();
        }
        String username = userService.getCurrentUser().getUsername();
        return request.records().stream()
                .map(record -> syncRecord(record, username))
                .toList();
    }

    private SyncFaceTemplateResult syncRecord(FaceTemplateDto record, String username) {
        String code = record == null ? null : record.employeeCode();
        try {
            save(record, username);
            return SyncFaceTemplateResult.sent(code);
        } catch (IllegalArgumentException e) {
            return SyncFaceTemplateResult.rejected(code, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Could not save face template for {}", code, e);
            return SyncFaceTemplateResult.rejected(code, "An unexpected server error occurred.");
        }
    }

    private void save(FaceTemplateDto record, String username) {
        if (record == null || record.employeeCode() == null || record.employeeCode().isBlank()) {
            throw new IllegalArgumentException("EMP No is required.");
        }
        String code = record.employeeCode().trim();
        if (!employeeRepository.existsById(code)) {
            throw new IllegalArgumentException("Employee " + code + " does not exist.");
        }
        if (!MODEL.equals(record.model())) {
            throw new IllegalArgumentException("Face model " + record.model() + " is not supported.");
        }
        byte[] embedding = decode(record.embedding());
        if (record.sampleCount() < 1) {
            throw new IllegalArgumentException("A face needs at least one sample.");
        }
        if (record.registeredAt() == null) {
            throw new IllegalArgumentException("Registration time is required.");
        }

        Optional<FaceTemplate> existing = faceTemplateRepository.findById(code);
        // Sent again, or a newer registration from another phone already arrived: keep the newest
        if (existing.isPresent() && !record.registeredAt().isAfter(existing.get().getRegisteredAt())) {
            return;
        }
        FaceTemplate template = existing.orElseGet(FaceTemplate::new);
        template.setEmployeeCode(code);
        template.setModel(record.model());
        template.setEmbedding(embedding);
        template.setSampleCount(record.sampleCount());
        template.setRegisteredAt(record.registeredAt());
        template.setRegisteredBy(username);
        template.setPhoto(photo(code, record.photo()));
        faceTemplateRepository.save(template);
    }

    /** The registered face photo of a worker, if one was sent. */
    @Transactional(readOnly = true)
    public Optional<byte[]> getPhoto(String employeeCode) {
        return faceTemplateRepository.findById(employeeCode).map(FaceTemplate::getPhoto);
    }

    // A bad photo never costs the registration: it is left out and logged
    private static byte[] photo(String code, String photo) {
        if (photo == null) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(photo);
        } catch (IllegalArgumentException e) {
            bytes = null;
        }
        if (!FacePhotos.isValid(bytes)) {
            log.warn("Face of {}: photo left out, not a JPEG of up to 200 KB", code);
            return null;
        }
        return bytes;
    }

    private static byte[] decode(String embedding) {
        byte[] bytes;
        try {
            bytes = embedding == null ? null : Base64.getDecoder().decode(embedding);
        } catch (IllegalArgumentException e) {
            bytes = null;
        }
        if (bytes == null || bytes.length != EMBEDDING_BYTES) {
            throw new IllegalArgumentException("The face template is not valid. Register the face again.");
        }
        return bytes;
    }

    private static FaceTemplateDto toDto(FaceTemplate template) {
        return new FaceTemplateDto(
                template.getEmployeeCode(),
                template.getModel(),
                Base64.getEncoder().encodeToString(template.getEmbedding()),
                template.getSampleCount(),
                template.getRegisteredAt(),
                template.getRegisteredBy(),
                null);
    }
}
