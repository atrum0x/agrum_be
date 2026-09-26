package com.atrum.agrum.permission;

import com.atrum.agrum.permission.dto.PermissionSetDto;
import com.atrum.agrum.permission.dto.ProjectionDto;
import com.atrum.agrum.permission.mapper.PermissionSetMapper;
import com.atrum.agrum.permission.mapper.ProjectionMapper;
import com.atrum.agrum.projection.Projection;
import com.atrum.agrum.projection.ProjectionRepository;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ProjectionRepository projectionRepository;
    private final PermissionSetRepository permissionSetRepository;
    private final AppUserRepository userRepository;
    private final ProjectionMapper projectionMapper;
    private final PermissionSetMapper permissionSetMapper;

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

    @Transactional(readOnly = true)
    public Set<ProjectionDto> grantedProjections(String permissionSetId) {
        PermissionSet ps = permissionSetRepository.findById(permissionSetId)
                .orElseThrow(() -> new RuntimeException("Permission Set not found"));
        return projectionMapper.toDtoSet(ps.getProjections());
    }

    @Transactional(readOnly = true)
    public List<PermissionSetDto> allPermissions(String search) {
        List<PermissionSet> ps;

        if (StringUtils.hasText(search)) {
            String query = search.trim();
            ps = permissionSetRepository.findByIdContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        } else {
            ps = permissionSetRepository.findAll();
        }

        return permissionSetMapper.toDtoSet(ps);
    }
}