package com.atrum.agrum.weight;

import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.DecideWeightChangeRequest;
import com.atrum.agrum.weight.dto.WeightChangeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeightApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T09:30:00Z");

    @Mock
    private WeightRecordRepository weightRecordRepository;
    @Mock
    private WeightChangeRequestRepository weightChangeRequestRepository;
    @Mock
    private UserService userService;

    private WeightApprovalService weightApprovalService;
    private WeightRecord weight;
    private AppUser approver;

    @BeforeEach
    void setUp() {
        weightApprovalService = new WeightApprovalService(weightRecordRepository, weightChangeRequestRepository,
                userService, Clock.fixed(NOW, ZoneId.of("Asia/Colombo")));
        weight = WeightChangeTestData.weighing();
        approver = WeightChangeTestData.user("approver1");
        when(userService.getCurrentUser()).thenReturn(approver);
        when(weightChangeRequestRepository.findWeightIdById(7L)).thenReturn(Optional.of(weight.getWeightId()));
        when(weightRecordRepository.findVisibleByIdForUpdate(weight.getWeightId())).thenReturn(Optional.of(weight));
    }

    @Test
    void TC79_approveCorrection13to14_usesTheSavedVarianceAndKeepsTheOriginal() {
        pending(ChangeType.CORRECTION, "14.00", WeightChangeTestData.user("sup1"));

        WeightChangeDto decided = weightApprovalService.approve(7L, new DecideWeightChangeRequest(0));

        assertThat(weight.getWeightKg()).isEqualTo("14.00");
        assertThat(weight.getWeightAfterVariance()).isEqualTo("14.28");
        assertThat(weight.getOriginalWeightKg()).isEqualTo("13.00");
        assertThat(weight.getLineState()).isEqualTo(LineState.RECORDED);
        assertThat(weight.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.requestStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.decidedBy()).isEqualTo("approver1");
        assertThat(decided.decidedAt()).isEqualTo(NOW);
    }

    @Test
    void TC80_disapprove_leavesTheWeightUnchanged() {
        pending(ChangeType.CORRECTION, "14.00", WeightChangeTestData.user("sup1"));

        WeightChangeDto decided = weightApprovalService.disapprove(7L, new DecideWeightChangeRequest(0));

        assertThat(weight.getWeightKg()).isEqualTo("13.00");
        assertThat(weight.getWeightAfterVariance()).isEqualTo("13.26");
        assertThat(weight.getApprovalStatus()).isEqualTo(ApprovalStatus.DISAPPROVED);
        assertThat(decided.requestStatus()).isEqualTo(ApprovalStatus.DISAPPROVED);
    }

    @Test
    void TC81_approveCancellation_cancelsTheWeighing() {
        pending(ChangeType.CANCELLATION, null, WeightChangeTestData.user("sup1"));

        weightApprovalService.approve(7L, new DecideWeightChangeRequest(0));

        assertThat(weight.getLineState()).isEqualTo(LineState.CANCELLED);
        assertThat(weight.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(weight.getWeightKg()).isEqualTo("13.00");
    }

    @Test
    void TC78_approverMayApproveTheirOwnRequest() {
        pending(ChangeType.CORRECTION, "14.00", approver);

        assertThat(weightApprovalService.approve(7L, new DecideWeightChangeRequest(0)).requestStatus())
                .isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void WH09_alreadyDecidedRequest_isRejected() {
        WeightChangeRequest request = pending(ChangeType.CORRECTION, "14.00", WeightChangeTestData.user("sup1"));
        request.setRequestStatus(ApprovalStatus.DISAPPROVED);

        assertThatThrownBy(() -> weightApprovalService.approve(7L, new DecideWeightChangeRequest(0)))
                .hasMessage(WeightMessages.WH_09_NOT_PENDING);
    }

    @Test
    void WH10_weighingChangedSinceShown_isRejected() {
        pending(ChangeType.CORRECTION, "14.00", WeightChangeTestData.user("sup1"));

        assertThatThrownBy(() -> weightApprovalService.approve(7L, new DecideWeightChangeRequest(1)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(weight.getWeightKg()).isEqualTo("13.00");
    }

    @Test
    void A11_requestAtAnInvisibleSite_isNotFound() {
        when(weightChangeRequestRepository.findWeightIdById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> weightApprovalService.approve(8L, new DecideWeightChangeRequest(0)))
                .hasMessageStartingWith("Weight change request not found");
    }

    private WeightChangeRequest pending(ChangeType changeType, String proposedKg, AppUser requester) {
        WeightChangeRequest request = WeightChangeTestData.pending(weight, changeType, proposedKg, requester);
        when(weightChangeRequestRepository.findVisibleById(7L)).thenReturn(Optional.of(request));
        return request;
    }
}
