package com.atrum.agrum.face;

import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.EmploymentStatus;
import com.atrum.agrum.face.dto.FaceRegistrationDto;
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

    /**
     * Same as the phone's match threshold: a face this close to another employee's face would be
     * identified as them at the scale, so it can't be registered to someone else.
     */
    static final double SAME_FACE = 0.60;

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
        // A face belongs to one employee; two phones may register it to different ones before they
        // have each other's faces, so the server checks too
        Optional<FaceTemplate> owner = sameFaceAs(code, embedding);
        if (owner.isPresent()) {
            String other = owner.get().getEmployeeCode();
            String name = employeeRepository.findById(other).map(e -> e.getFullName() + " (" + other + ")").orElse(other);
            throw new IllegalArgumentException("This face is already registered to " + name
                    + ". A face can belong to only one employee.");
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

    /** A worker's registered face, for the employee page; empty when they have none. */
    @Transactional(readOnly = true)
    public Optional<FaceRegistrationDto> getRegistration(String employeeCode) {
        return faceTemplateRepository.findById(employeeCode).map(template -> new FaceRegistrationDto(
                template.getEmployeeCode(),
                template.getRegisteredAt(),
                template.getRegisteredBy(),
                template.getSampleCount(),
                template.getPhoto() != null));
    }

    /** Removes a worker's registered face; phones drop it at their next site-data refresh. */
    @Transactional
    public boolean removeTemplate(String employeeCode) {
        if (!faceTemplateRepository.existsById(employeeCode)) {
            return false;
        }
        faceTemplateRepository.deleteById(employeeCode);
        return true;
    }

    // The closest face of another employee, if it is close enough to be the same person
    private Optional<FaceTemplate> sameFaceAs(String employeeCode, byte[] embedding) {
        float[] candidate = floats(embedding);
        FaceTemplate best = null;
        double bestSimilarity = SAME_FACE;
        for (FaceTemplate other : faceTemplateRepository.findAll()) {
            if (other.getEmployeeCode().equals(employeeCode) || !MODEL.equals(other.getModel())
                    || other.getEmbedding() == null || other.getEmbedding().length != EMBEDDING_BYTES) {
                continue;
            }
            double similarity = cosine(candidate, floats(other.getEmbedding()));
            if (similarity >= bestSimilarity) {
                bestSimilarity = similarity;
                best = other;
            }
        }
        return Optional.ofNullable(best);
    }

    // 512 float32 values, little-endian, as the phone sends them
    private static float[] floats(byte[] bytes) {
        java.nio.FloatBuffer buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] values = new float[buffer.remaining()];
        buffer.get(values);
        return values;
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
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
