package com.atrum.agrum.weight;

import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.DecideWeightChangeRequest;
import com.atrum.agrum.weight.dto.WeightChangeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Approve / Disapprove (FSD 4.3–4.6). WH-08 is enforced by granting these endpoints only to
 * approvers. The requester may approve their own request (D-08). The request and the
 * weighing are updated in one transaction; the 10 pm summary picks the change up via decided_at.
 */
@Service
@RequiredArgsConstructor
public class WeightApprovalService {

    private final WeightRecordRepository weightRecordRepository;
    private final WeightChangeRequestRepository weightChangeRequestRepository;
    private final UserService userService;
    private final Clock clock;

    /** Pending requests at the user's sites, oldest first. */
    @Transactional(readOnly = true)
    public List<WeightChangeDto> getPending() {
        return weightChangeRequestRepository.findPending().stream()
                .map(WeightChangeDto::from)
                .toList();
    }

    /** Dashboard count at the user's sites. */
    @Transactional(readOnly = true)
    public long countPending() {
        return weightChangeRequestRepository.countPending();
    }

    /**
     * Correction: the weight in use becomes the requested one, recalculated with the weighing's
     * saved variance; the original weight never changes. Cancellation: the weighing stops counting
     * toward totals and the daily limit, and can't be changed again.
     */
    @Transactional
    public WeightChangeDto approve(Long changeId, DecideWeightChangeRequest decision) {
        AppUser user = userService.getCurrentUser();
        WeightChangeRequest request = findPending(changeId, decision);
        WeightRecord weight = request.getWeight();
        if (request.getChangeType() == ChangeType.CORRECTION) {
            weight.setWeightKg(request.getProposedWeightKg());
            weight.setWeightAfterVariance(
                    WeightCalculator.afterVariance(request.getProposedWeightKg(), weight.getVariancePercent()));
        } else {
            weight.setLineState(LineState.CANCELLED);
        }
        return decide(request, ApprovalStatus.APPROVED, user);
    }

    /** Nothing on the weighing changes except its approval status. */
    @Transactional
    public WeightChangeDto disapprove(Long changeId, DecideWeightChangeRequest decision) {
        AppUser user = userService.getCurrentUser();
        return decide(findPending(changeId, decision), ApprovalStatus.DISAPPROVED, user);
    }

    private WeightChangeRequest findPending(Long changeId, DecideWeightChangeRequest decision) {
        String notFound = "Weight change request not found: " + changeId;
        // Lock the weighing before loading anything else, so two approvers deciding at once are
        // handled one after the other and the second one reads the first one's result
        WeightRecord weight = weightChangeRequestRepository.findWeightIdById(changeId)
                .flatMap(weightRecordRepository::findVisibleByIdForUpdate)
                .orElseThrow(() -> new IllegalArgumentException(notFound));
        WeightChangeRequest request = weightChangeRequestRepository.findVisibleById(changeId)
                .orElseThrow(() -> new IllegalArgumentException(notFound));
        // WH-09
        if (request.getRequestStatus() != ApprovalStatus.PENDING_AUTHORIZATION) {
            throw new IllegalArgumentException(WeightMessages.WH_09_NOT_PENDING);
        }
        WeightChangeService.checkVersion(weight, decision.weightRowVersion());
        return request;
    }

    private WeightChangeDto decide(WeightChangeRequest request, ApprovalStatus status, AppUser user) {
        request.setRequestStatus(status);
        request.setDecidedBy(user);
        request.setDecidedAt(clock.instant());
        request.getWeight().setApprovalStatus(status);
        weightChangeRequestRepository.saveAndFlush(request);
        // Bumps row_version, so a pop-up still showing the old version gets WH-10
        weightRecordRepository.saveAndFlush(request.getWeight());
        return WeightChangeDto.from(request);
    }
}
