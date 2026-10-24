package com.atrum.agrum.weight;

import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.user.AppUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * E001's second weighing at S01 (+2%): 13.00 kg, 13.26 after variance.
 */
final class WeightChangeTestData {

    private WeightChangeTestData() {}

    static WeightRecord weighing() {
        Employee employee = new Employee();
        employee.setEmployeeCode("E001");

        WeightRecord weight = new WeightRecord();
        weight.setWeightId(UUID.randomUUID());
        weight.setEmployee(employee);
        weight.setEstateId("S01");
        weight.setWeighDate(LocalDate.of(2026, 10, 24));
        weight.setEntrySequence(2);
        weight.setOriginalWeightKg(new BigDecimal("13.00"));
        weight.setWeightKg(new BigDecimal("13.00"));
        weight.setVariancePercent(new BigDecimal("2.00"));
        weight.setWeightAfterVariance(new BigDecimal("13.26"));
        weight.setRowVersion(0);
        return weight;
    }

    static AppUser user(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        return user;
    }

    static WeightChangeRequest pending(WeightRecord weight, ChangeType changeType, String proposedKg, AppUser requester) {
        WeightChangeRequest request = new WeightChangeRequest();
        request.setId(7L);
        request.setWeight(weight);
        request.setEstateId(weight.getEstateId());
        request.setChangeType(changeType);
        request.setPreviousWeightKg(weight.getWeightKg());
        request.setProposedWeightKg(proposedKg == null ? null : new BigDecimal(proposedKg));
        request.setComment("Scale misread");
        request.setRequestStatus(ApprovalStatus.PENDING_AUTHORIZATION);
        request.setRequestedBy(requester);
        weight.setApprovalStatus(ApprovalStatus.PENDING_AUTHORIZATION);
        return request;
    }
}
