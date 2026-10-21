package com.atrum.agrum.user;

import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.exception.ResourceNotFoundException;
import com.atrum.agrum.user.dto.AppUserDto;
import com.atrum.agrum.user.dto.CreateUserRequest;
import com.atrum.agrum.user.mapper.AppUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AppUserDto create(CreateUserRequest request) {
        if (request.employeeCode() == null || request.employeeCode().isBlank()) {
            throw new IllegalArgumentException("Employee code is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        Employee employee = findEmployee(request.employeeCode());
        if (employee.getUserAccount() != null) {
            throw new IllegalArgumentException(
                    "Employee already has a user account: " + request.employeeCode());
        }

        String username = employee.getEmployeeCode();
        if (userRepository.existsById(username)) {
            throw new IllegalArgumentException("Username is already in use: " + username);
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmployee(employee);

        AppUser savedUser = userRepository.save(user);
        employee.setUserAccount(savedUser);
        return userMapper.toDto(savedUser);
    }

    @Transactional(readOnly = true)
    public List<AppUserDto> findAll() {
        return userMapper.toDtoList(userRepository.findAll());
    }

    @Transactional(readOnly = true)
    public AppUserDto findByUsername(String username) {
        return userMapper.toDto(getUser(username));
    }

    @Transactional
    public void delete(String username) {
        userRepository.delete(getUser(username));
    }

    private AppUser getUser(String username) {
        return userRepository.findById(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));
    }

    private Employee findEmployee(String employeeCode) {
        return employeeRepository.findById(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + employeeCode));
    }
}
