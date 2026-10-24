package com.atrum.agrum.weight;

import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.RequestCancellationRequest;
import com.atrum.agrum.weight.dto.RequestCorrectionRequest;
import com.atrum.agrum.weight.dto.WeightChangeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeightChangeServiceTest {

    @Mock
    private WeightRecordRepository weightRecordRepository;
    @Mock
    private WeightChangeRequestRepository weightChangeRequestRepository;
    @Mock
    private UserService userService;
    @InjectMocks
    private WeightChangeService weightChangeService;

    private WeightRecord weight;

    @BeforeEach
    void setUp() {
        weight = WeightChangeTestData.weighing();
        when(userService.getCurrentUser()).thenReturn(WeightChangeTestData.user("sup1"));
        when(weightRecordRepository.findVisibleByIdForUpdate(weight.getWeightId())).thenReturn(Optional.of(weight));
        when(weightChangeRequestRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void TC75_correctionSubmitted_isPendingAndTheWeightIsUnchanged() {
        WeightChangeDto change = weightChangeService.requestCorrection(weight.getWeightId(),
                new RequestCorrectionRequest(new BigDecimal("14.00"), " Scale misread ", 0));

        assertThat(change.requestStatus()).isEqualTo(ApprovalStatus.PENDING_AUTHORIZATION);
        assertThat(change.changeType()).isEqualTo(ChangeType.CORRECTION);
        assertThat(change.previousWeightKg()).isEqualTo("13.00");
        assertThat(change.proposedWeightKg()).isEqualTo("14.00");
        assertThat(change.proposedWeightAfterVariance()).isEqualTo("14.28");
        assertThat(change.comment()).isEqualTo("Scale misread");
        assertThat(change.requestedBy()).isEqualTo("sup1");
        assertThat(weight.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING_AUTHORIZATION);
        assertThat(weight.getWeightKg()).isEqualTo("13.00");
        assertThat(weight.getWeightAfterVariance()).isEqualTo("13.26");

        ArgumentCaptor<WeightChangeRequest> saved = ArgumentCaptor.forClass(WeightChangeRequest.class);
        verify(weightChangeRequestRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEstateId()).isEqualTo("S01");
    }

    @Test
    void cancellationSubmitted_isPendingWithNoProposedWeight() {
        WeightChangeDto change = weightChangeService.requestCancellation(weight.getWeightId(),
                new RequestCancellationRequest("Weighed twice", 0));

        assertThat(change.changeType()).isEqualTo(ChangeType.CANCELLATION);
        assertThat(change.proposedWeightKg()).isNull();
        assertThat(weight.getLineState()).isEqualTo(LineState.RECORDED);
    }

    @Test
    void TC73_correctionWithoutComment_isRejected() {
        assertThatThrownBy(() -> weightChangeService.requestCorrection(weight.getWeightId(),
                new RequestCorrectionRequest(new BigDecimal("14.00"), "  ", 0)))
                .hasMessage(WeightMessages.WH_07_COMMENT);
    }

    @Test
    void WH06_correctedWeightZeroOrUnchanged_isRejected() {
        for (String kg : new String[]{"0", "13.00", "13", "14.005"}) {
            assertThatThrownBy(() -> weightChangeService.requestCorrection(weight.getWeightId(),
                    new RequestCorrectionRequest(new BigDecimal(kg), "x", 0)))
                    .hasMessage(WeightMessages.WH_06_CORRECTED_WEIGHT);
        }
    }

    @Test
    void TC76_secondRequestWhilePending_isRejected() {
        when(weightChangeRequestRepository.existsByWeightWeightIdAndRequestStatus(
                weight.getWeightId(), ApprovalStatus.PENDING_AUTHORIZATION)).thenReturn(true);

        assertThatThrownBy(() -> weightChangeService.requestCancellation(weight.getWeightId(),
                new RequestCancellationRequest("x", 0)))
                .hasMessage(WeightMessages.WH_05_ALREADY_PENDING);
        verify(weightChangeRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void TC82_requestOnCancelledRecord_isRejected() {
        weight.setLineState(LineState.CANCELLED);

        assertThatThrownBy(() -> weightChangeService.requestCorrection(weight.getWeightId(),
                new RequestCorrectionRequest(new BigDecimal("14.00"), "x", 0)))
                .hasMessage(WeightMessages.WH_04_CANCELLED);
    }

    @Test
    void WH10_staleVersion_isRejected() {
        assertThatThrownBy(() -> weightChangeService.requestCancellation(weight.getWeightId(),
                new RequestCancellationRequest("x", 3)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void A11_invisibleWeighing_isNotFound() {
        when(weightRecordRepository.findVisibleByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> weightChangeService.requestCancellation(weight.getWeightId(),
                new RequestCancellationRequest("x", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Weight record not found");
    }
}
