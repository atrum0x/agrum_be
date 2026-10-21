package com.atrum.agrum.employee;

import com.atrum.agrum.employee.dto.CreateEmployeeRequest;
import com.atrum.agrum.employee.dto.EmployeeDto;
import com.atrum.agrum.employee.dto.UpdateEmployeeRequest;
import com.atrum.agrum.estate.dto.EstateDto;
import com.atrum.agrum.permission.dto.PermissionSetDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    public ResponseEntity<EmployeeDto> create(@RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<EmployeeDto>> getAllEmployees(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(employeeService.findAll(search));
    }

    @GetMapping("/{employeeCode}")
    public ResponseEntity<EmployeeDto> findByCode(@PathVariable String employeeCode) {
        return ResponseEntity.ok(employeeService.findByCode(employeeCode));
    }

    @GetMapping("/grantedPermissions/{employeeCode}")
    public ResponseEntity<List<PermissionSetDto>> getPermissionSets(
            @PathVariable String employeeCode,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employeeService.getGrantedPermissionSets(employeeCode, search));
    }

    @GetMapping("grantedEstates/{employeeCode}")
    public ResponseEntity<List<EstateDto>> getGrantedEstates(
            @PathVariable String employeeCode,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employeeService.getGrantedEstates(employeeCode, search));
    }

    @PutMapping("/{employeeCode}")
    public ResponseEntity<EmployeeDto> update(
            @PathVariable String employeeCode,
            @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(employeeService.update(employeeCode, request));
    }

    @DeleteMapping("/{employeeCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String employeeCode) {
        employeeService.delete(employeeCode);
    }
}
