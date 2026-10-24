package com.atrum.agrum.employee;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import com.atrum.agrum.employee.dto.CreateEmployeeRequest;
import com.atrum.agrum.employee.dto.EmployeeDto;
import com.atrum.agrum.employee.dto.SetPasswordRequest;
import com.atrum.agrum.employee.dto.UpdateEmployeeRequest;
import com.atrum.agrum.estate.dto.EstateDto;
import com.atrum.agrum.permission.dto.PermissionSetDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ProjectionGroup("Person Basic Data")
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "Create an employee (and optionally their login)")
    @PostMapping
    public ResponseEntity<EmployeeDto> create(@RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @Operation(summary = "Search employees. Needed to open Users (Person Basic Data)")
    @GetMapping
    public ResponseEntity<List<EmployeeDto>> getAllEmployees(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(employeeService.findAll(search));
    }

    @Operation(summary = "View one employee")
    @GetMapping("/{employeeCode}")
    public ResponseEntity<EmployeeDto> findByCode(@PathVariable String employeeCode) {
        return ResponseEntity.ok(employeeService.findByCode(employeeCode));
    }

    @Operation(summary = "View the permission sets an employee's login has")
    @GetMapping("/grantedPermissions/{employeeCode}")
    public ResponseEntity<List<PermissionSetDto>> getPermissionSets(
            @PathVariable String employeeCode,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employeeService.getGrantedPermissionSets(employeeCode, search));
    }

    @Operation(summary = "List permission sets an employee's login does not have yet")
    @GetMapping("/unassignedPermissions/{employeeCode}")
    public ResponseEntity<List<PermissionSetDto>> getUnassignedPermissionSets(
            @PathVariable String employeeCode,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employeeService.getUnassignedPermissionSets(employeeCode, search));
    }

    @Operation(summary = "View the sites an employee's login can see")
    @GetMapping("grantedEstates/{employeeCode}")
    public ResponseEntity<List<EstateDto>> getGrantedEstates(
            @PathVariable String employeeCode,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employeeService.getGrantedEstates(employeeCode, search));
    }

    @Operation(summary = "List sites an employee's login cannot see yet")
    @GetMapping("/unassignedEstates/{employeeCode}")
    public ResponseEntity<List<EstateDto>> getUnassignedEstates(
            @PathVariable String employeeCode,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(employeeService.getUnassignedEstates(employeeCode, search));
    }

    @Operation(summary = "Take permission sets away from an employee's login")
    @PostMapping("/revokePermissions/{employeeCode}")
    public ResponseEntity<String> revokePermissionSets(
            @PathVariable String employeeCode,
            @RequestBody List<String> permissionSetIds) {
        employeeService.revokePermissionSets(employeeCode, permissionSetIds);
        return ResponseEntity.ok("Permission Sets revoked from Employee " + employeeCode);
    }

    @Operation(summary = "Give permission sets to an employee's login")
    @PostMapping("/grantPermissions/{employeeCode}")
    public ResponseEntity<String> grantPermissionSets(
            @PathVariable String employeeCode,
            @RequestBody List<String> permissionSetIds) {
        employeeService.grantPermissionSets(employeeCode, permissionSetIds);
        return ResponseEntity.ok("Permission Sets granted to Employee " + employeeCode);
    }

    @Operation(summary = "Stop an employee's login seeing sites")
    @PostMapping("/revokeEstates/{employeeCode}")
    public ResponseEntity<String> revokeEstates(
            @PathVariable String employeeCode,
            @RequestBody List<String> estateIds) {
        employeeService.revokeEstates(employeeCode, estateIds);
        return ResponseEntity.ok("Estates revoked from Employee " + employeeCode);
    }

    @Operation(summary = "Let an employee's login see sites")
    @PostMapping("/grantEstates/{employeeCode}")
    public ResponseEntity<String> grantEstates(
            @PathVariable String employeeCode,
            @RequestBody List<String> estateIds) {
        employeeService.grantEstates(employeeCode, estateIds);
        return ResponseEntity.ok("Estates granted to Employee " + employeeCode);
    }

    @Operation(summary = "Change an employee's details or status")
    @PutMapping("/{employeeCode}")
    public ResponseEntity<EmployeeDto> update(
            @PathVariable String employeeCode,
            @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(employeeService.update(employeeCode, request));
    }

    @Operation(summary = "Set a new password for an employee's login (administrators)")
    @PutMapping("/{employeeCode}/password")
    public ResponseEntity<String> setPassword(
            @PathVariable String employeeCode,
            @RequestBody SetPasswordRequest request) {
        employeeService.setPassword(employeeCode, request.newPassword());
        return ResponseEntity.ok("Password updated for Employee " + employeeCode);
    }

    @Operation(summary = "Delete an employee entered by mistake (only without attendance or login)")
    @DeleteMapping("/{employeeCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String employeeCode) {
        employeeService.delete(employeeCode);
    }
}
