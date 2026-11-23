package com.atrum.agrum.field;

import com.atrum.agrum.division.Division;
import com.atrum.agrum.division.DivisionRepository;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.estate.SiteMessages;
import com.atrum.agrum.field.dto.CreateFieldRequest;
import com.atrum.agrum.field.dto.FieldDto;
import com.atrum.agrum.field.dto.UpdateFieldRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;

@Service
public class FieldService {

    private static final int MAX_CODE_LENGTH = 30;
    private static final int MAX_DESCRIPTION_LENGTH = 50;

    private final FieldRepository fieldRepository;
    private final DivisionRepository divisionRepository;
    private final EstateRepository estateRepository;

    public FieldService(FieldRepository fieldRepository,
                        DivisionRepository divisionRepository,
                        EstateRepository estateRepository) {
        this.fieldRepository = fieldRepository;
        this.divisionRepository = divisionRepository;
        this.estateRepository = estateRepository;
    }

    @Transactional(readOnly = true)
    public List<FieldDto> getFields(String estateId, String divisionCode, String search) {
        // Postgres cannot type a null parameter in ":search IS NULL", so pass "" for no filter
        return fieldRepository.findAllByEstate(estateId, term(divisionCode), term(search)).stream()
                .map(FieldDto::from)
                .toList();
    }

    @Transactional
    public FieldDto createField(String estateId, CreateFieldRequest request) {
        if (!estateRepository.existsById(estateId)) {
            throw new IllegalArgumentException("Site not found: " + estateId);
        }
        String fieldCode = requireCode(request.fieldCode());
        // SB-09: unique within the site; save() would silently replace an existing one
        if (fieldRepository.existsById(new FieldId(estateId, fieldCode))) {
            throw new IllegalArgumentException(SiteMessages.SB_09_FIELD_EXISTS);
        }

        Division division = requireDivision(estateId, request.divisionCode());
        String description = requireDescription(request.description());
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        // SB-10: the new field wins. Cleared first, because a unique index allows one default per division
        if (isDefault) {
            fieldRepository.clearDefaultExcept(estateId, division.getDivisionCode(), fieldCode);
        }

        Field field = new Field();
        field.setFieldCode(fieldCode);
        field.setEstateId(estateId);
        field.setDivision(division);
        field.setDescription(description);
        field.setDefault(isDefault);
        return FieldDto.from(fieldRepository.save(field));
    }

    @Transactional
    public FieldDto updateField(String estateId, String fieldCode, UpdateFieldRequest request) {
        Field current = findInEstate(estateId, fieldCode);
        String description = requireDescription(request.description());
        // A field can move to another division at any time (FSD 2.3 B.3). Saved weighings and
        // attendance keep the division they were recorded with
        boolean moved = StringUtils.hasText(request.divisionCode())
                && !request.divisionCode().trim().equals(current.getDivisionCode());
        String divisionCode = moved
                ? requireDivision(estateId, request.divisionCode()).getDivisionCode()
                : current.getDivisionCode();
        boolean isDefault = request.isDefault() != null ? request.isDefault() : current.isDefault();
        // SB-10: the field just saved wins. Cleared first (this also clears the persistence context)
        if (isDefault) {
            fieldRepository.clearDefaultExcept(estateId, divisionCode, fieldCode);
        }

        Field field = findInEstate(estateId, fieldCode);
        field.setDescription(description);
        field.setDivisionCode(divisionCode);
        field.setDefault(isDefault);
        fieldRepository.saveAndFlush(field);
        if (moved) {
            fieldRepository.moveEmployeeDefaults(estateId, fieldCode, divisionCode);
        }

        String newCode = StringUtils.hasText(request.fieldCode()) ? requireCode(request.fieldCode()) : fieldCode;
        if (!newCode.equals(fieldCode)) {
            changeCode(estateId, fieldCode, newCode);
        }
        return FieldDto.from(findInEstate(estateId, newCode));
    }

    @Transactional
    public FieldDto setDefault(String estateId, String fieldCode) {
        Field field = findInEstate(estateId, fieldCode);
        // Cleared first, because a unique index allows one default per division. The bulk update
        // clears the persistence context, so re-read afterwards
        fieldRepository.clearDefaultExcept(estateId, field.getDivisionCode(), fieldCode);
        field = findInEstate(estateId, fieldCode);
        field.setDefault(true);
        return FieldDto.from(fieldRepository.save(field));
    }

    @Transactional
    public void deleteFields(String estateId, List<String> fieldCodes) {
        if (fieldCodes == null || fieldCodes.isEmpty()) {
            throw new IllegalArgumentException("Select at least one field to delete");
        }
        List<Field> fields = fieldRepository.findAllInEstate(estateId, fieldCodes);
        if (fields.size() != new HashSet<>(fieldCodes).size()) {
            throw new IllegalArgumentException("Some fields were not found in site " + estateId);
        }
        // SB-12: a person default or an attendance record uses it
        rejectIfUsed(estateId, fieldCodes);
        fieldRepository.deleteAll(fields);
    }

    private void changeCode(String estateId, String oldCode, String newCode) {
        // SB-09 and SB-12: a new code must be free, and only an unused field can change its code
        if (fieldRepository.existsById(new FieldId(estateId, newCode))) {
            throw new IllegalArgumentException(SiteMessages.SB_09_FIELD_EXISTS);
        }
        rejectIfUsed(estateId, List.of(oldCode));
        fieldRepository.changeCode(estateId, oldCode, newCode);
    }

    private void rejectIfUsed(String estateId, List<String> fieldCodes) {
        List<String> employeeCodes = fieldRepository.findEmployeesUsingAsDefault(estateId, fieldCodes);
        if (!employeeCodes.isEmpty()) {
            throw new IllegalArgumentException("Field is the default for employees: "
                    + String.join(", ", employeeCodes.stream().limit(5).toList())
                    + (employeeCodes.size() > 5 ? " and " + (employeeCodes.size() - 5) + " more" : ""));
        }
        if (fieldRepository.isUsedInAttendance(estateId, fieldCodes)) {
            throw new IllegalArgumentException(SiteMessages.SB_12_IN_USE);
        }
    }

    private Field findInEstate(String estateId, String fieldCode) {
        return fieldRepository.findInEstate(estateId, fieldCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Field " + fieldCode + " not found in site " + estateId));
    }

    private Division requireDivision(String estateId, String divisionCode) {
        // SB-09: Division is mandatory and must be one of the site's
        if (!StringUtils.hasText(divisionCode)) {
            throw new IllegalArgumentException("Please select the Division.");
        }
        return divisionRepository.findInEstate(estateId, divisionCode.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Division " + divisionCode.trim() + " not found in site " + estateId));
    }

    private static String requireCode(String code) {
        // SB-09
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Please enter the Field.");
        }
        String trimmed = code.trim();
        if (trimmed.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Field must be at most " + MAX_CODE_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String requireDescription(String description) {
        // SB-09
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("Please enter the Field Description.");
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Field Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String term(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
