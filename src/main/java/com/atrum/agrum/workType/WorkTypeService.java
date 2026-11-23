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
import java.util.Locale;

@Service
public class WorkTypeService {

    private static final int MAX_CODE_LENGTH = 20;
    private static final int MAX_NAME_LENGTH = 50;

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
        // SB-16: mandatory, up to 20 characters, typed text becomes capitals (FSD 2.3 B.4)
        if (!StringUtils.hasText(request.workTypeCode())) {
            throw new IllegalArgumentException("Please enter the Work Type ID.");
        }
        String code = request.workTypeCode().trim().toUpperCase(Locale.ROOT);
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Work Type ID must be at most " + MAX_CODE_LENGTH + " characters.");
        }
        // SB-16: unique within the site; save() would silently replace an existing one
        if (workTypeRepository.existsById(new WorkTypeId(estateId, code))) {
            throw new IllegalArgumentException(SiteMessages.SB_16_WORK_TYPE_ID_EXISTS);
        }
        String name = requireName(estateId, request.name(), code);

        WorkType workType = new WorkType();
        workType.setWorkTypeCode(code);
        workType.setEstateId(estateId);
        workType.setName(name);
        workType.setWeighingRequired(requireWeighingRequired(request.weighingRequired()));
        return WorkTypeDto.from(workTypeRepository.save(workType));
    }

    @Transactional
    public WorkTypeDto updateWorkType(String estateId, String code, UpdateWorkTypeRequest request) {
        WorkType workType = workTypeRepository.findInEstate(estateId, code)
                .orElseThrow(() -> new IllegalArgumentException("Work type " + code + " not found in site " + estateId));
        String name = requireName(estateId, request.name(), code);
        // SB-12: the name can't change once attendance uses the work type
        if (!name.equals(workType.getName()) && workTypeRepository.isUsedInAttendance(estateId, List.of(code))) {
            throw new IllegalArgumentException(SiteMessages.SB_12_IN_USE);
        }
        boolean weighingRequired = requireWeighingRequired(request.weighingRequired());
        // SB-13
        if (workType.isWeighingRequired() && !weighingRequired
                && workTypeRepository.isWeighedOn(estateId, code, LocalDate.now(clock))) {
            throw new IllegalArgumentException(SiteMessages.SB_13_WEIGHED_TODAY);
        }
        workType.setName(name);
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

    private String requireName(String estateId, String name, String code) {
        // SB-11: mandatory and unique within the site
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Please enter the Work Type.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Work Type must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (workTypeRepository.nameExists(estateId, trimmed, code)) {
            throw new IllegalArgumentException(SiteMessages.SB_11_WORK_TYPE_EXISTS);
        }
        return trimmed;
    }

    private boolean requireWeighingRequired(Boolean weighingRequired) {
        if (weighingRequired == null) {
            throw new IllegalArgumentException("Weighing required is required");
        }
        return weighingRequired;
    }
}
