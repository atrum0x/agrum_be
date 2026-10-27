package com.atrum.agrum.employee;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.auth.AuthService;
import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.collectionPoint.CollectionPointId;
import com.atrum.agrum.collectionPoint.CollectionPointRepository;
import com.atrum.agrum.employee.dto.CreateEmployeeRequest;
import com.atrum.agrum.employee.dto.EmployeeDto;
import com.atrum.agrum.employee.dto.UpdateEmployeeRequest;
import com.atrum.agrum.employee.mapper.EmployeeMapper;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.estate.dto.EstateDto;
import com.atrum.agrum.estate.mapper.EstateMapper;
import com.atrum.agrum.exception.ResourceNotFoundException;
import com.atrum.agrum.permission.PermissionSet;
import com.atrum.agrum.permission.PermissionSetRepository;
import com.atrum.agrum.permission.dto.PermissionSetDto;
import com.atrum.agrum.permission.mapper.PermissionSetMapper;
import com.atrum.agrum.security.PermissionCacheEvictor;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.user.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final int MAX_EMPLOYEE_NO_LENGTH = 30;

    private final EmployeeRepository employeeRepository;
    private final EstateRepository estateRepository;
    private final CollectionPointRepository collectionPointRepository;
    private final PermissionSetRepository permissionSetRepository;
    private final EstateMapper estateMapper;
    private final EmployeeMapper employeeMapper;
    private final PermissionSetMapper permissionSetMapper;
    private final UserService userService;
    private final PermissionCacheEvictor permissionCacheEvictor;
    private final AuthService authService;

    @Transactional
    public EmployeeDto create(CreateEmployeeRequest request) {
        // PB-01
        if (!StringUtils.hasText(request.employeeCode()) || request.employeeCode().trim().length() > MAX_EMPLOYEE_NO_LENGTH) {
            throw new IllegalArgumentException(PersonMessages.PB_01_EMPLOYEE_NO);
        }
        String employeeCode = request.employeeCode().trim().toUpperCase(Locale.ROOT);
        if (employeeRepository.existsById(employeeCode)) {
            throw new IllegalArgumentException(PersonMessages.PB_01_EMPLOYEE_NO_EXISTS);
        }
        String nic = validatePerson(employeeCode, request.firstName(), request.lastName(), request.nic(),
                request.employeeCategory(), request.startDate(), request.endDate(), request.homeEstateId(),
                request.defaultEstateId());
        if (request.isUserAccount() && (request.password() == null || request.password().isBlank())) {
            throw new IllegalArgumentException("Password is required when creating a user account");
        }

        Employee employee = employeeMapper.fromCreateRequest(request);
        employee.setEmployeeCode(employeeCode);
        employee.setNic(nic);
        applyEstates(employee, request.defaultEstateId(), request.defaultCollectionPointCode(), request.homeEstateId());
        Employee savedEmployee = employeeRepository.save(employee);

        if (request.isUserAccount()) {
            userService.create(new CreateUserRequest(savedEmployee.getEmployeeCode(), request.password()));
            // PB-10: the Default Site is one of the Assigned Sites, so the new login can see it
            savedEmployee.getUserAccount().addAllowedEstate(savedEmployee.getDefaultEstate());
            permissionCacheEvictor.evictUserEstates(savedEmployee.getUserAccount().getUsername());
        }

        return employeeMapper.toDto(savedEmployee);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> findAll(String search) {
        List<Employee> employees;

        if (StringUtils.hasText(search)) {
            String query = search.trim();
            employees = employeeRepository.searchEmployees(query);
        } else {
            employees = employeeRepository.findAll();
        }

        return employees.stream()
                .map(employeeMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeDto findByCode(String employeeCode) {
        return employeeMapper.toDto(getEmployee(employeeCode));
    }

    @Transactional
    public EmployeeDto update(String employeeCode, UpdateEmployeeRequest request) {
        Employee employee = getEmployee(employeeCode);
        String nic = validatePerson(employeeCode, request.firstName(), request.lastName(), request.nic(),
                request.employeeCategory(), request.startDate(), request.endDate(), request.homeEstateId(),
                request.defaultEstateId());
        // PB-10: a login's Default Site must be one of its granted (assigned) sites
        if (employee.getUserAccount() != null && employee.getUserAccount().getAllowedEstates().stream()
                .noneMatch(estate -> estate.getId().equals(request.defaultEstateId().trim()))) {
            throw new IllegalArgumentException(PersonMessages.PB_10_DEFAULT_SITE);
        }
        boolean wasActive = employee.getEmploymentStatus() == EmploymentStatus.ACTIVE;
        employeeMapper.updateEmployee(request, employee);
        employee.setNic(nic);
        applyEstates(employee, request.defaultEstateId(), request.defaultCollectionPointCode(), request.homeEstateId());
        Employee saved = employeeRepository.save(employee);
        // LG-04: an inactive employee cannot log in, so end their current sessions too
        if (wasActive && saved.getEmploymentStatus() != EmploymentStatus.ACTIVE && saved.getUserAccount() != null) {
            authService.revokeSessions(saved.getUserAccount().getUsername());
        }
        return employeeMapper.toDto(saved);
    }

    @Transactional
    public void setPassword(String employeeCode, String newPassword) {
        Employee employee = getEmployee(employeeCode);
        if (employee.getUserAccount() == null) {
            throw new IllegalArgumentException("Employee has no user account: " + employeeCode);
        }
        String username = employee.getUserAccount().getUsername();
        userService.setPassword(username, newPassword);
        // Force existing sessions to log in again with the new password
        authService.revokeSessions(username);
    }

    @Transactional
    public void delete(String employeeCode) {
        Employee employee = getEmployee(employeeCode);
        if (employee.getUserAccount() != null) {
            throw new IllegalStateException(
                    "Employee has a user account and cannot be deleted: " + employeeCode);
        }
        // Setup records are made Inactive, not deleted (FSD 1.9); delete is only for one entered by mistake
        if (employeeRepository.hasAttendance(employeeCode)) {
            throw new IllegalArgumentException(
                    "Employee has attendance records and cannot be deleted. Set the status to Inactive instead.");
        }
        employeeRepository.delete(employee);
    }

    /** PB-02 to PB-08 and PB-10. Returns the NIC as stored: trimmed and in capitals (FSD: uppercase). */
    private String validatePerson(String employeeCode, String firstName, String lastName, String nic,
                                  AttendanceCategory category, LocalDate startDate, LocalDate endDate,
                                  String homeEstateId, String defaultEstateId) {
        // PB-02: the FSD has one Name; agrum keeps first and last name
        if (!StringUtils.hasText(firstName) || !StringUtils.hasText(lastName)) {
            throw new IllegalArgumentException(PersonMessages.PB_02_NAME);
        }
        // PB-03
        if (!StringUtils.hasText(nic)) {
            throw new IllegalArgumentException(PersonMessages.PB_03_NIC);
        }
        String normalizedNic = nic.trim().toUpperCase(Locale.ROOT);
        // PB-04 (the unique column also refuses it, but with no readable message)
        employeeRepository.findFirstByNicIgnoreCaseAndEmployeeCodeNot(normalizedNic, employeeCode)
                .ifPresent(other -> {
                    throw new IllegalArgumentException(PersonMessages.nicExists(other.getEmployeeCode()));
                });
        // PB-05
        if (category == null) {
            throw new IllegalArgumentException(PersonMessages.PB_05_CATEGORY);
        }
        // PB-06
        if (startDate == null) {
            throw new IllegalArgumentException(PersonMessages.PB_06_START_DATE);
        }
        // PB-07
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(PersonMessages.PB_07_END_DATE);
        }
        // PB-08
        if (!StringUtils.hasText(homeEstateId)) {
            throw new IllegalArgumentException(PersonMessages.PB_08_HOME_SITE);
        }
        // PB-10: exactly one Default Site
        if (!StringUtils.hasText(defaultEstateId)) {
            throw new IllegalArgumentException(PersonMessages.PB_10_DEFAULT_SITE);
        }
        return normalizedNic;
    }

    private Employee getEmployee(String employeeCode) {
        return employeeRepository.findById(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + employeeCode));
    }

    private void applyEstates(Employee employee, String defaultEstateId, String defaultCollectionPointCode,
                              String homeEstateId) {
        Estate defaultEstate = findEstate(defaultEstateId);
        employee.setDefaultEstate(defaultEstate);
        employee.setDefaultCollectionPoint(findDefaultCollectionPoint(defaultEstate, defaultCollectionPointCode));
        employee.setHomeEstate(findEstate(homeEstateId));
    }

    // findById is not estate-filtered, so an existing value still saves for users without that site
    private CollectionPoint findDefaultCollectionPoint(Estate defaultEstate, String cpCode) {
        if (!StringUtils.hasText(cpCode)) {
            return null;
        }
        if (defaultEstate == null) {
            throw new IllegalArgumentException("Select a default site before choosing a default collection point");
        }
        // PB-11
        return collectionPointRepository.findById(new CollectionPointId(defaultEstate.getId(), cpCode.trim()))
                .orElseThrow(() -> new IllegalArgumentException(PersonMessages.PB_11_DEFAULT_COLLECTION_POINT));
    }

    private Estate findEstate(String estateId) {
        if (estateId == null || estateId.isBlank()) {
            return null;
        }
        return estateRepository.findById(estateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estate not found: " + estateId));
    }

    @Transactional(readOnly = true)
    public List<PermissionSetDto> getGrantedPermissionSets(String employeeCode, String search) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";

        return permissionSetMapper.toDtoSet(
                employeeRepository.findGrantedPermissionSets(employeeCode, searchTerm));
    }

    @Transactional(readOnly = true)
    public List<PermissionSetDto> getUnassignedPermissionSets(String employeeCode, String search) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";

        return permissionSetMapper.toDtoSet(
                employeeRepository.findUnassignedPermissionSets(employeeCode, searchTerm));
    }

    @Transactional(readOnly = true)
    public List<EstateDto> getGrantedEstates(String employeeCode, String search) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";

        return estateMapper.toDtoList(
                employeeRepository.findGrantedEstates(employeeCode, searchTerm));
    }

    @Transactional(readOnly = true)
    public List<EstateDto> getUnassignedEstates(String employeeCode, String search) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";

        return estateMapper.toDtoList(
                employeeRepository.findUnassignedEstates(employeeCode, searchTerm));
    }

    @Transactional
    public void revokePermissionSets(String employeeCode, List<String> permissionSetIds) {
        if (permissionSetIds == null || permissionSetIds.isEmpty()) return;

        Employee employee = getEmployee(employeeCode);
        if (employee.getUserAccount() == null) {
            throw new IllegalStateException("Employee has no user account: " + employeeCode);
        }

        List<PermissionSet> permissionSets = permissionSetRepository.findAllById(permissionSetIds);
        permissionSets.forEach(employee.getUserAccount()::removePermissionSet);
        permissionCacheEvictor.evictUser(employee.getUserAccount().getUsername());
    }

    @Transactional
    public void grantPermissionSets(String employeeCode, List<String> permissionSetIds) {
        if (permissionSetIds == null || permissionSetIds.isEmpty()) return;

        Employee employee = getEmployee(employeeCode);
        if (employee.getUserAccount() == null) {
            throw new IllegalStateException("Employee has no user account: " + employeeCode);
        }

        List<PermissionSet> permissionSets = permissionSetRepository.findAllById(permissionSetIds);
        permissionSets.forEach(employee.getUserAccount()::addPermissionSet);
        permissionCacheEvictor.evictUser(employee.getUserAccount().getUsername());
    }

    @Transactional
    public void revokeEstates(String employeeCode, List<String> estateIds) {
        if (estateIds == null || estateIds.isEmpty()) return;

        Employee employee = getEmployee(employeeCode);
        if (employee.getUserAccount() == null) {
            throw new IllegalStateException("Employee has no user account: " + employeeCode);
        }

        // PB-10: the Default Site stays assigned; change the Default Site first
        if (employee.getDefaultEstate() != null && estateIds.contains(employee.getDefaultEstate().getId())) {
            throw new IllegalArgumentException(PersonMessages.PB_10_DEFAULT_SITE);
        }
        List<Estate> estates = estateRepository.findAllById(estateIds);
        estates.forEach(employee.getUserAccount()::removeAllowedEstate);
        permissionCacheEvictor.evictUserEstates(employee.getUserAccount().getUsername());
    }

    @Transactional
    public void grantEstates(String employeeCode, List<String> estateIds) {
        if (estateIds == null || estateIds.isEmpty()) return;

        Employee employee = getEmployee(employeeCode);
        if (employee.getUserAccount() == null) {
            throw new IllegalStateException("Employee has no user account: " + employeeCode);
        }

        List<Estate> estates = estateRepository.findAllById(estateIds);
        estates.forEach(employee.getUserAccount()::addAllowedEstate);
        permissionCacheEvictor.evictUserEstates(employee.getUserAccount().getUsername());
    }
}
