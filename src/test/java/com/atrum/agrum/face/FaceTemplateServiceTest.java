package com.atrum.agrum.face;

import com.atrum.agrum.attendance.dto.SyncStatus;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.face.dto.FaceRegistrationDto;
import com.atrum.agrum.face.dto.FaceTemplateDto;
import com.atrum.agrum.face.dto.SyncFaceTemplateRequest;
import com.atrum.agrum.face.dto.SyncFaceTemplateResult;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Faces registered on phones: each gets its own Sent / Rejected answer, and the newest registration wins. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FaceTemplateServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-30T04:00:00Z");

    @Mock
    private FaceTemplateRepository faceTemplateRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserService userService;

    private FaceTemplateService service;

    @BeforeEach
    void setUp() {
        service = new FaceTemplateService(faceTemplateRepository, employeeRepository, userService,
                Clock.fixed(T0, ZoneOffset.UTC));
        AppUser user = new AppUser();
        user.setUsername("sup1");
        when(userService.getCurrentUser()).thenReturn(user);
        when(employeeRepository.existsById(any())).thenReturn(true);
        when(faceTemplateRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void aBadTemplateIsRejectedAndTheOthersAreStillSaved() {
        when(employeeRepository.existsById("E404")).thenReturn(false);
        FaceTemplateDto shortEmbedding = new FaceTemplateDto("E002", FaceTemplateService.MODEL,
                Base64.getEncoder().encodeToString(new byte[10]), 5, T0, null, null);

        List<SyncFaceTemplateResult> results = service.syncTemplates(new SyncFaceTemplateRequest(List.of(
                template("E001", T0), shortEmbedding, template("E404", T0), template("E003", T0))));

        assertThat(results).extracting(SyncFaceTemplateResult::status)
                .containsExactly(SyncStatus.SENT, SyncStatus.REJECTED, SyncStatus.REJECTED, SyncStatus.SENT);
        assertThat(results.get(1).reason()).isEqualTo("The face template is not valid. Register the face again.");
        assertThat(results.get(2).reason()).isEqualTo("Employee E404 does not exist.");
    }

    @Test
    void aSavedTemplateRecordsWhoSentIt() {
        service.syncTemplates(new SyncFaceTemplateRequest(List.of(template("E001", T0))));

        ArgumentCaptor<FaceTemplate> saved = ArgumentCaptor.forClass(FaceTemplate.class);
        verify(faceTemplateRepository).save(saved.capture());
        assertThat(saved.getValue().getEmployeeCode()).isEqualTo("E001");
        assertThat(saved.getValue().getRegisteredBy()).isEqualTo("sup1");
        assertThat(saved.getValue().getEmbedding()).hasSize(FaceTemplateService.EMBEDDING_BYTES);
    }

    @Test
    void theFacePhotoIsKept_andABadOneIsLeftOutWithoutRejectingTheFace() {
        String jpeg = Base64.getEncoder().encodeToString(new byte[]{(byte) 0xFF, (byte) 0xD8, 1});
        FaceTemplateDto withPhoto = withPhoto(template("E001", T0), jpeg);
        FaceTemplateDto badPhoto = withPhoto(template("E002", T0), "not base64!");

        List<SyncFaceTemplateResult> results =
                service.syncTemplates(new SyncFaceTemplateRequest(List.of(withPhoto, badPhoto)));

        assertThat(results).extracting(SyncFaceTemplateResult::status).containsOnly(SyncStatus.SENT);
        ArgumentCaptor<FaceTemplate> saved = ArgumentCaptor.forClass(FaceTemplate.class);
        verify(faceTemplateRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getPhoto()).hasSize(3);
        assertThat(saved.getAllValues().get(1).getPhoto()).isNull();
    }

    private static FaceTemplateDto withPhoto(FaceTemplateDto t, String photo) {
        return new FaceTemplateDto(t.employeeCode(), t.model(), t.embedding(), t.sampleCount(), t.registeredAt(),
                null, photo);
    }

    @Test
    void anOlderRegistrationDoesNotReplaceANewerOne_butIsStillSent() {
        FaceTemplate newer = new FaceTemplate();
        newer.setEmployeeCode("E001");
        newer.setRegisteredAt(T0.plusSeconds(60));
        when(faceTemplateRepository.findById("E001")).thenReturn(Optional.of(newer));

        List<SyncFaceTemplateResult> results =
                service.syncTemplates(new SyncFaceTemplateRequest(List.of(template("E001", T0))));

        assertThat(results.getFirst().status()).isEqualTo(SyncStatus.SENT);
        verify(faceTemplateRepository, never()).save(any());
    }

    @Test
    void anotherModelIsRejected() {
        FaceTemplateDto facenet = new FaceTemplateDto("E001", "facenet_128",
                Base64.getEncoder().encodeToString(new byte[FaceTemplateService.EMBEDDING_BYTES]), 5, T0, null, null);

        List<SyncFaceTemplateResult> results = service.syncTemplates(new SyncFaceTemplateRequest(List.of(facenet)));

        assertThat(results.getFirst().status()).isEqualTo(SyncStatus.REJECTED);
        assertThat(results.getFirst().reason()).isEqualTo("Face model facenet_128 is not supported.");
    }

    @Test
    void aFaceAlreadyRegisteredToAnotherEmployeeIsRejected() {
        when(faceTemplateRepository.findAll()).thenReturn(List.of(stored("E009", 0, T0)));
        Employee owner = new Employee();
        owner.setFirstName("Randev");
        owner.setLastName("Perera");
        when(employeeRepository.findById("E009")).thenReturn(Optional.of(owner));

        List<SyncFaceTemplateResult> results =
                service.syncTemplates(new SyncFaceTemplateRequest(List.of(face("E001", 0, T0.plusSeconds(60)))));

        assertThat(results.getFirst().status()).isEqualTo(SyncStatus.REJECTED);
        assertThat(results.getFirst().reason()).isEqualTo(
                "This face is already registered to Randev Perera (E009). A face can belong to only one employee.");
        verify(faceTemplateRepository, never()).save(any());
    }

    @Test
    void registeringTheSameEmployeeAgainIsNotADuplicate() {
        FaceTemplate own = stored("E001", 0, T0);
        when(faceTemplateRepository.findAll()).thenReturn(List.of(own));
        when(faceTemplateRepository.findById("E001")).thenReturn(Optional.of(own));

        List<SyncFaceTemplateResult> results =
                service.syncTemplates(new SyncFaceTemplateRequest(List.of(face("E001", 0, T0.plusSeconds(60)))));

        assertThat(results.getFirst().status()).isEqualTo(SyncStatus.SENT);
        verify(faceTemplateRepository).save(any());
    }

    @Test
    void anotherEmployeesDifferentFaceIsFine() {
        when(faceTemplateRepository.findAll()).thenReturn(List.of(stored("E009", 1, T0)));

        List<SyncFaceTemplateResult> results =
                service.syncTemplates(new SyncFaceTemplateRequest(List.of(face("E001", 0, T0))));

        assertThat(results.getFirst().status()).isEqualTo(SyncStatus.SENT);
    }

    @Test
    void theEmployeePageGetsWhenAndByWhom_andWhetherThereIsAPhoto() {
        FaceTemplate template = stored("E001", 0, T0);
        template.setRegisteredBy("sup1");
        template.setSampleCount(5);
        when(faceTemplateRepository.findById("E001")).thenReturn(Optional.of(template));

        FaceRegistrationDto registration = service.getRegistration("E001").orElseThrow();

        assertThat(registration.registeredAt()).isEqualTo(T0);
        assertThat(registration.registeredBy()).isEqualTo("sup1");
        assertThat(registration.sampleCount()).isEqualTo(5);
        assertThat(registration.hasPhoto()).isFalse();
        assertThat(service.getRegistration("NOPE")).isEmpty();
    }

    @Test
    void aFaceCanBeRemoved() {
        when(faceTemplateRepository.existsById("E001")).thenReturn(true);

        assertThat(service.removeTemplate("E001")).isTrue();
        verify(faceTemplateRepository).deleteById("E001");
        assertThat(service.removeTemplate("NOPE")).isFalse();
    }

    // A unit embedding pointing along one axis: the same axis is the same face, another axis a different one
    private static byte[] unit(int axis) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(FaceTemplateService.EMBEDDING_BYTES)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(axis * Float.BYTES, 1f);
        return buffer.array();
    }

    private static FaceTemplate stored(String code, int axis, Instant registeredAt) {
        FaceTemplate template = new FaceTemplate();
        template.setEmployeeCode(code);
        template.setModel(FaceTemplateService.MODEL);
        template.setEmbedding(unit(axis));
        template.setRegisteredAt(registeredAt);
        return template;
    }

    private static FaceTemplateDto face(String code, int axis, Instant registeredAt) {
        return new FaceTemplateDto(code, FaceTemplateService.MODEL, Base64.getEncoder().encodeToString(unit(axis)), 5,
                registeredAt, null, null);
    }

    private static FaceTemplateDto template(String code, Instant registeredAt) {
        return new FaceTemplateDto(code, FaceTemplateService.MODEL,
                Base64.getEncoder().encodeToString(new byte[FaceTemplateService.EMBEDDING_BYTES]), 5, registeredAt, null, null);
    }
}
