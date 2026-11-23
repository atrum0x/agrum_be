package com.atrum.agrum.division;

import com.atrum.agrum.division.dto.CreateDivisionRequest;
import com.atrum.agrum.division.dto.DivisionDto;
import com.atrum.agrum.division.dto.UpdateDivisionRequest;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.estate.SiteMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;

@Service
public class DivisionService {

    private static final int MAX_CODE_LENGTH = 30;
    private static final int MAX_DESCRIPTION_LENGTH = 50;

    private final DivisionRepository divisionRepository;
    private final EstateRepository estateRepository;

    public DivisionService(DivisionRepository divisionRepository, EstateRepository estateRepository) {
        this.divisionRepository = divisionRepository;
        this.estateRepository = estateRepository;
    }

    @Transactional(readOnly = true)
    public List<DivisionDto> getDivisions(String estateId) {
        return divisionRepository.findAllByEstate(estateId).stream()
                .map(DivisionDto::from)
                .toList();
    }

    @Transactional
    public DivisionDto createDivision(String estateId, CreateDivisionRequest request) {
        if (!estateRepository.existsById(estateId)) {
            throw new IllegalArgumentException("Site not found: " + estateId);
        }
        String code = requireCode(request.divisionCode());
        // SB-15: unique within the site; save() would silently replace an existing one
        if (divisionRepository.existsById(new DivisionId(estateId, code))) {
            throw new IllegalArgumentException(SiteMessages.SB_15_DIVISION_EXISTS);
        }

        Division division = new Division();
        division.setEstateId(estateId);
        division.setDivisionCode(code);
        division.setDescription(requireDescription(request.description()));
        return DivisionDto.from(divisionRepository.save(division));
    }

    @Transactional
    public DivisionDto updateDivision(String estateId, String divisionCode, UpdateDivisionRequest request) {
        Division division = findInEstate(estateId, divisionCode);
        division.setDescription(requireDescription(request.description()));
        divisionRepository.saveAndFlush(division);

        String newCode = StringUtils.hasText(request.divisionCode()) ? requireCode(request.divisionCode()) : divisionCode;
        if (newCode.equals(divisionCode)) {
            return DivisionDto.from(division);
        }
        // SB-15 and SB-12: a new code must be free, and only an unused division can change its code
        if (divisionRepository.existsById(new DivisionId(estateId, newCode))) {
            throw new IllegalArgumentException(SiteMessages.SB_15_DIVISION_EXISTS);
        }
        if (divisionRepository.hasFields(estateId, List.of(divisionCode))
                || divisionRepository.isUsed(estateId, List.of(divisionCode))) {
            throw new IllegalArgumentException(SiteMessages.SB_12_IN_USE);
        }
        divisionRepository.changeCode(estateId, divisionCode, newCode);
        return DivisionDto.from(findInEstate(estateId, newCode));
    }

    @Transactional
    public void deleteDivisions(String estateId, List<String> divisionCodes) {
        if (divisionCodes == null || divisionCodes.isEmpty()) {
            throw new IllegalArgumentException("Select at least one division to delete");
        }
        List<Division> divisions = divisionRepository.findAllInEstate(estateId, divisionCodes);
        if (divisions.size() != new HashSet<>(divisionCodes).size()) {
            throw new IllegalArgumentException("Some divisions were not found in site " + estateId);
        }
        // SB-17
        if (divisionRepository.hasFields(estateId, divisionCodes)) {
            throw new IllegalArgumentException(SiteMessages.SB_17_DIVISION_HAS_FIELDS);
        }
        // SB-12
        if (divisionRepository.isUsed(estateId, divisionCodes)) {
            throw new IllegalArgumentException(SiteMessages.SB_12_IN_USE);
        }
        divisionRepository.deleteAll(divisions);
    }

    private Division findInEstate(String estateId, String divisionCode) {
        return divisionRepository.findInEstate(estateId, divisionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Division " + divisionCode + " not found in site " + estateId));
    }

    private static String requireCode(String code) {
        // SB-15
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Please enter the Division Code.");
        }
        String trimmed = code.trim();
        if (trimmed.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Division Code must be at most " + MAX_CODE_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String requireDescription(String description) {
        // SB-15
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("Please enter the Division Description.");
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Division Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return trimmed;
    }
}
