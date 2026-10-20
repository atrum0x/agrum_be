package com.atrum.agrum.employee;

import com.atrum.agrum.employee.dto.CreateEmployeeRequest;
import com.atrum.agrum.employee.dto.EmployeeDto;
import com.atrum.agrum.employee.dto.UpdateEmployeeRequest;
import com.atrum.agrum.employee.mapper.EmployeeMapper;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EstateRepository estateRepository;
    private final EmployeeMapper employeeMapper;

    @Transactional
    public EmployeeDto create(CreateEmployeeRequest request) {
        if (employeeRepository.existsById(request.employeeCode())) {
            throw new IllegalArgumentException("Employee code is already in use: " + request.employeeCode());
        }

        Employee employee = employeeMapper.fromCreateRequest(request);
        applyEstates(employee, request.defaultEstateId(), request.homeEstateId());
        return employeeMapper.toDto(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> findAll() {
        return employeeRepository.findAll().stream()
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
        employeeMapper.updateEmployee(request, employee);
        applyEstates(employee, request.defaultEstateId(), request.homeEstateId());
        return employeeMapper.toDto(employeeRepository.save(employee));
    }

    @Transactional
    public void delete(String employeeCode) {
        Employee employee = getEmployee(employeeCode);
        if (employee.getUserAccount() != null) {
            throw new IllegalStateException(
                    "Employee has a user account and cannot be deleted: " + employeeCode);
        }
        employeeRepository.delete(employee);
    }

    private Employee getEmployee(String employeeCode) {
        return employeeRepository.findById(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + employeeCode));
    }

    private void applyEstates(Employee employee, String defaultEstateId, String homeEstateId) {
        employee.setDefaultEstate(findEstate(defaultEstateId));
        employee.setHomeEstate(findEstate(homeEstateId));
    }

    private Estate findEstate(String estateId) {
        if (estateId == null || estateId.isBlank()) {
            return null;
        }
        return estateRepository.findById(estateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estate not found: " + estateId));
    }
}
