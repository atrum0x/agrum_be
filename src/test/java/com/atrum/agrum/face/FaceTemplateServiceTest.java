package com.atrum.agrum.face;

import com.atrum.agrum.attendance.dto.SyncStatus;
import com.atrum.agrum.employee.EmployeeRepository;
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
                Base64.getEncoder().encodeToString(new byte[10]), 5, T0, null);

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
                Base64.getEncoder().encodeToString(new byte[FaceTemplateService.EMBEDDING_BYTES]), 5, T0, null);

        List<SyncFaceTemplateResult> results = service.syncTemplates(new SyncFaceTemplateRequest(List.of(facenet)));

        assertThat(results.getFirst().status()).isEqualTo(SyncStatus.REJECTED);
        assertThat(results.getFirst().reason()).isEqualTo("Face model facenet_128 is not supported.");
    }

    private static FaceTemplateDto template(String code, Instant registeredAt) {
        return new FaceTemplateDto(code, FaceTemplateService.MODEL,
                Base64.getEncoder().encodeToString(new byte[FaceTemplateService.EMBEDDING_BYTES]), 5, registeredAt, null);
    }
}
