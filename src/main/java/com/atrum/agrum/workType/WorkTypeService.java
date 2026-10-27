package com.atrum.agrum.workType;

import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.estate.SiteMessages;
import com.atrum.agrum.workType.dto.CreateWorkTypeRequest;
import com.atrum.agrum.workType.dto.UpdateWorkTypeRequest;
import com.atrum.agrum.workType.dto.WorkTypeDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

@Service
public class WorkTypeService {

    private static final int MAX_WORK_TYPE_LENGTH = 50;

    private final WorkTypeRepository workTypeRepository;
    private final EstateRepository estateRepository;
    private final Clock clock;

    public WorkTypeService(WorkTypeRepository workTypeRepository, EstateRepository estateRepository, Clock clock) {
        this.workTypeRepository = workTypeRepository;
        this.estateRepository = estateRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkTypeDto> getWorkTypes(String estateId) {
        return workTypeRepository.findAllByEstate(estateId).stream()
                .map(WorkTypeDto::from)
                .toList();
    }

    @Transactional
    public WorkTypeDto createWorkType(String estateId, CreateWorkTypeRequest request) {
        if (!estateRepository.existsById(estateId)) {
            throw new IllegalArgumentException("Site not found: " + estateId);
        }
        if (!StringUtils.hasText(request.workType())) {
            throw new IllegalArgumentException("Work type is required");
        }
        String code = request.workType().trim();
        if (code.length() > MAX_WORK_TYPE_LENGTH) {
            throw new IllegalArgumentException("Work type must be at most " + MAX_WORK_TYPE_LENGTH + " characters");
        }
        // SB-11: unique within the site; save() would silently replace an existing one
        if (workTypeRepository.existsById(new WorkTypeId(estateId, code))) {
            throw new IllegalArgumentException(SiteMessages.SB_11_WORK_TYPE_EXISTS);
        }

        WorkType workType = new WorkType();
        workType.setWorkType(code);
        workType.setEstateId(estateId);
        workType.setWeighingRequired(requireWeighingRequired(request.weighingRequired()));
        return WorkTypeDto.from(workTypeRepository.save(workType));
    }

    @Transactional
    public WorkTypeDto updateWorkType(String estateId, String code, UpdateWorkTypeRequest request) {
        WorkType workType = workTypeRepository.findInEstate(estateId, code)
                .orElseThrow(() -> new IllegalArgumentException("Work type " + code + " not found in site " + estateId));
        boolean weighingRequired = requireWeighingRequired(request.weighingRequired());
        // SB-13
        if (workType.isWeighingRequired() && !weighingRequired
                && workTypeRepository.isWeighedOn(estateId, code, LocalDate.now(clock))) {
            throw new IllegalArgumentException(SiteMessages.SB_13_WEIGHED_TODAY);
        }
        workType.setWeighingRequired(weighingRequired);
        return WorkTypeDto.from(workTypeRepository.save(workType));
    }

    @Transactional
    public void deleteWorkTypes(String estateId, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("Select at least one work type to delete");
        }
        List<WorkType> workTypes = workTypeRepository.findAllInEstate(estateId, codes);
        if (workTypes.size() != new HashSet<>(codes).size()) {
            throw new IllegalArgumentException("Some work types were not found in site " + estateId);
        }
        // SB-12
        if (workTypeRepository.isUsedInAttendance(estateId, codes)) {
            throw new IllegalArgumentException(SiteMessages.SB_12_IN_USE);
        }
        workTypeRepository.deleteAll(workTypes);
    }

    private boolean requireWeighingRequired(Boolean weighingRequired) {
        if (weighingRequired == null) {
            throw new IllegalArgumentException("Weighing required is required");
        }
        return weighingRequired;
    }
}
