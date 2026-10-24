package com.atrum.agrum.weight;

import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.RequestCancellationRequest;
import com.atrum.agrum.weight.dto.RequestCorrectionRequest;
import com.atrum.agrum.weight.dto.WeightChangeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request a correction or cancellation (FSD 2.8, 4.2). WH-03 is enforced by granting the
 * request endpoints separately. Nothing on the weighing changes until the request is approved,
 * except its approval status, which shows Pending Authorization.
 */
@Service
@RequiredArgsConstructor
public class WeightChangeService {

    private static final int MAX_COMMENT_LENGTH = 300;

    private final WeightRecordRepository weightRecordRepository;
    private final WeightChangeRequestRepository weightChangeRequestRepository;
    private final UserService userService;

    @Transactional
    public WeightChangeDto requestCorrection(UUID weightId, RequestCorrectionRequest request) {
        AppUser user = userService.getCurrentUser();
        WeightRecord weight = findChangeable(weightId, request.rowVersion());
        String comment = requireComment(request.comment());
        // WH-06
        BigDecimal corrected = request.correctedWeightKg();
        if (!WeightCalculator.isValidWeight(corrected) || corrected.compareTo(weight.getWeightKg()) == 0) {
            throw new IllegalArgumentException(WeightMessages.WH_06_CORRECTED_WEIGHT);
        }
        return submit(weight, ChangeType.CORRECTION, corrected, comment, user);
    }

    @Transactional
    public WeightChangeDto requestCancellation(UUID weightId, RequestCancellationRequest request) {
        AppUser user = userService.getCurrentUser();
        WeightRecord weight = findChangeable(weightId, request.rowVersion());
        String comment = requireComment(request.comment());
        return submit(weight, ChangeType.CANCELLATION, null, comment, user);
    }

    // Locked so two requests for the same weighing are checked one after the other (WH-05)
    private WeightRecord findChangeable(UUID weightId, Integer rowVersion) {
        WeightRecord weight = weightRecordRepository.findVisibleByIdForUpdate(weightId)
                .orElseThrow(() -> new IllegalArgumentException("Weight record not found: " + weightId));
        checkVersion(weight, rowVersion);
        // WH-04
        if (weight.getLineState() == LineState.CANCELLED) {
            throw new IllegalArgumentException(WeightMessages.WH_04_CANCELLED);
        }
        // WH-05
        if (weightChangeRequestRepository.existsByWeightWeightIdAndRequestStatus(
                weightId, ApprovalStatus.PENDING_AUTHORIZATION)) {
            throw new IllegalArgumentException(WeightMessages.WH_05_ALREADY_PENDING);
        }
        return weight;
    }

    private WeightChangeDto submit(WeightRecord weight, ChangeType changeType, BigDecimal proposedWeightKg,
                                   String comment, AppUser user) {
        WeightChangeRequest request = new WeightChangeRequest();
        request.setWeight(weight);
        request.setEstateId(weight.getEstateId());
        request.setChangeType(changeType);
        request.setPreviousWeightKg(weight.getWeightKg());
        request.setProposedWeightKg(proposedWeightKg);
        request.setComment(comment);
        request.setRequestStatus(ApprovalStatus.PENDING_AUTHORIZATION);
        request.setRequestedBy(user);
        weight.setApprovalStatus(ApprovalStatus.PENDING_AUTHORIZATION);
        try {
            weightChangeRequestRepository.saveAndFlush(request);
            weightRecordRepository.saveAndFlush(weight);
        } catch (DataIntegrityViolationException e) {
            // uq_wcr_one_pending: another request was submitted after the WH-05 check
            throw new IllegalArgumentException(WeightMessages.WH_05_ALREADY_PENDING, e);
        }
        return WeightChangeDto.from(request);
    }

    // WH-07
    private static String requireComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            throw new IllegalArgumentException(WeightMessages.WH_07_COMMENT);
        }
        String trimmed = comment.trim();
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }
        return trimmed;
    }

    // WH-10: the weighing must be unchanged since the user opened it
    static void checkVersion(WeightRecord weight, Integer rowVersion) {
        if (rowVersion == null || !rowVersion.equals(weight.getRowVersion())) {
            throw new ObjectOptimisticLockingFailureException(WeightRecord.class, weight.getWeightId());
        }
    }
}
