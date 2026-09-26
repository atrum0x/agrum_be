package com.atrum.agrum.estate;

import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EstateService {

    private final EstateRepository estateRepository;
    private final AppUserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public EstateService(EstateRepository estateRepository,
                         AppUserRepository userRepository,
                         StringRedisTemplate redisTemplate) {
        this.estateRepository = estateRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    // Create or Update Estate
    public Estate saveEstate(Estate estate) {
        return estateRepository.save(estate);
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
