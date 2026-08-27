package com.atrum.agrum.security;

import com.atrum.agrum.permission.PermissionSet;
import com.atrum.agrum.permission.PermissionSetRepository;
import com.atrum.agrum.projection.Projection;
import com.atrum.agrum.projection.ProjectionRepository;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminPermissionService {

    private final ProjectionRepository projectionRepository;
    private final PermissionSetRepository permissionSetRepository;
    private final AppUserRepository userRepository;

    public AdminPermissionService(ProjectionRepository projectionRepository,
                                  PermissionSetRepository permissionSetRepository,
                                  AppUserRepository userRepository) {
        this.projectionRepository = projectionRepository;
        this.permissionSetRepository = permissionSetRepository;
        this.userRepository = userRepository;
    }

    public List<Projection> getAllProjections() {
        return projectionRepository.findAll();
    }

    public PermissionSet createPermissionSet(PermissionSet permissionSet) {
        return permissionSetRepository.save(permissionSet);
    }

    @Transactional
    public void grantProjectionToPermissionSet(String permissionSetId, String projectionId) {
        PermissionSet ps = permissionSetRepository.findById(permissionSetId)
                .orElseThrow(() -> new RuntimeException("Permission Set not found"));
        Projection proj = projectionRepository.findById(projectionId)
                .orElseThrow(() -> new RuntimeException("Projection not found"));

        ps.addProjection(proj);
        permissionSetRepository.save(ps);
    }

    @Transactional
    public void grantPermissionSetToUser(String username, String permissionSetId) {
        AppUser user = userRepository.findById(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        PermissionSet ps = permissionSetRepository.findById(permissionSetId)
                .orElseThrow(() -> new RuntimeException("Permission Set not found"));

        user.addPermissionSet(ps);
        userRepository.save(user);
    }
}