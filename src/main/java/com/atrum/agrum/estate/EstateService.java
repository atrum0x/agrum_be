package com.atrum.agrum.estate;

import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.dto.EmployeeDto;
import com.atrum.agrum.employee.mapper.EmployeeMapper;
import com.atrum.agrum.estate.dto.CreateEstateRequest;
import com.atrum.agrum.estate.dto.UpdateEstateRequest;
import com.atrum.agrum.exception.ResourceNotFoundException;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
public class EstateService {

    private final EstateRepository estateRepository;
    private final AppUserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;
    private final StringRedisTemplate redisTemplate;

    public EstateService(EstateRepository estateRepository,
                         AppUserRepository userRepository,
                         EmployeeRepository employeeRepository,
                         EmployeeMapper employeeMapper,
                         StringRedisTemplate redisTemplate) {
        this.estateRepository = estateRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.employeeMapper = employeeMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> getGrantedUsers(String estateId, String search) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";
        return employeeRepository.findUsersGrantedEstate(estateId, searchTerm).stream()
                .map(employeeMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> getUnassignedUsers(String estateId, String search) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";
        return employeeRepository.findUsersNotGrantedEstate(estateId, searchTerm).stream()
                .map(employeeMapper::toDto)
                .toList();
    }

    @Transactional
    public void grantUsers(String estateId, List<String> employeeCodes) {
        changeUserGrants(estateId, employeeCodes, AppUser::addAllowedEstate);
    }

    @Transactional
    public void revokeUsers(String estateId, List<String> employeeCodes) {
        changeUserGrants(estateId, employeeCodes, AppUser::removeAllowedEstate);
    }

    private void changeUserGrants(String estateId, List<String> employeeCodes, BiConsumer<AppUser, Estate> change) {
        if (employeeCodes == null || employeeCodes.isEmpty()) {
            throw new IllegalArgumentException("Select at least one user");
        }
        Estate estate = estateRepository.findById(estateId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found: " + estateId));
        List<Employee> employees = employeeRepository.findAllById(employeeCodes);
        if (employees.size() != new HashSet<>(employeeCodes).size()) {
            throw new IllegalArgumentException("Some employees were not found");
        }
        for (Employee employee : employees) {
            if (employee.getUserAccount() == null) {
                throw new IllegalArgumentException("Employee has no user account: " + employee.getEmployeeCode());
            }
        }
        for (Employee employee : employees) {
            AppUser user = employee.getUserAccount();
            change.accept(user, estate);
            evictUserEstates(user.getUsername());
        }
    }

    // The estate filter reads this cache before the DB; a stale entry keeps old access.
    // Tokens issued before the change still carry the old estates claim until refreshed.
    private void evictUserEstates(String username) {
        try {
            redisTemplate.delete("userEstates::" + username);
        } catch (Exception e) {
            System.err.println("Valkey down! DB updated successfully.");
        }
    }

    @Transactional
    public Estate createEstate(CreateEstateRequest request) {
        if (!StringUtils.hasText(request.id())) {
            throw new IllegalArgumentException("Site ID is required");
        }
        String estateId = request.id().trim();
        // save() would silently replace an existing estate
        if (estateRepository.existsById(estateId)) {
            throw new IllegalArgumentException("Site already exists: " + estateId);
        }

        Estate estate = new Estate();
        estate.setId(estateId);
        applyFields(estate, request.name(), request.address(), request.active(), request.fullDayQtyKg(),
                request.halfDayQtyKg(), request.maxDailyTransactions(), request.variancePercent());
        return estateRepository.save(estate);
    }

    @Transactional
    public Estate updateEstate(String estateId, UpdateEstateRequest request) {
        Estate estate = estateRepository.findById(estateId)
                .orElseThrow(() -> new ResourceNotFoundException("Estate not found: " + estateId));
        applyFields(estate, request.name(), request.address(), request.active(), request.fullDayQtyKg(),
                request.halfDayQtyKg(), request.maxDailyTransactions(), request.variancePercent());
        return estateRepository.save(estate);
    }

    // These columns are NOT NULL, so reject missing values with a 400 instead of a DB error
    private void applyFields(Estate estate, String name, String address, Boolean active, Integer fullDayQtyKg,
                             Integer halfDayQtyKg, Integer maxDailyTransactions, Float variancePercent) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Name is required");
        }
        if (fullDayQtyKg == null || halfDayQtyKg == null || maxDailyTransactions == null || variancePercent == null) {
            throw new IllegalArgumentException(
                    "Full day qty, half day qty, max daily transactions and variance % are required");
        }
        estate.setName(name.trim());
        estate.setAddress(StringUtils.hasText(address) ? address.trim() : null);
        estate.setActive(active == null || active);
        estate.setFullDayQtyKg(fullDayQtyKg);
        estate.setHalfDayQtyKg(halfDayQtyKg);
        estate.setMaxDailyTransactions(maxDailyTransactions);
        estate.setVariancePercent(variancePercent);
    }

    // List all Estates for Admin management
    public List<Estate> getAllEstates() {
        return estateRepository.findAll();
    }

    // Assign a list of Estate IDs to a User
    @Transactional
    public void assignEstatesToUser(String username, List<String> estateIds) {
        AppUser user = userRepository.findById(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<Estate> estates = estateRepository.findAllById(estateIds);
        user.setAllowedEstates(new HashSet<>(estates));
        userRepository.save(user);

        // Evict Valkey / Redis cache instantly
        try {
            redisTemplate.delete("userEstates::" + username);
        } catch (Exception e) {
            System.err.println("Valkey down! DB updated successfully.");
        }
    }

    // Get assigned estate IDs for a specific user
    @Transactional(readOnly = true)
    public List<String> getUserAllowedEstateIds(String username) {
        AppUser user = userRepository.findById(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        return user.getAllowedEstates().stream()
                .map(Estate::getId)
                .collect(Collectors.toList());
    }

}
