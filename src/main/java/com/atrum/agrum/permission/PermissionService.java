package com.atrum.agrum.permission;

import com.atrum.agrum.permission.dto.PermissionSetDto;
import com.atrum.agrum.projection.dto.ProjectionDto;
import com.atrum.agrum.permission.mapper.PermissionSetMapper;
import com.atrum.agrum.projection.mapper.ProjectionMapper;
import com.atrum.agrum.projection.Projection;
import com.atrum.agrum.projection.ProjectionRepository;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import com.atrum.agrum.user.dto.AppUserDto;
import com.atrum.agrum.user.mapper.AppUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ProjectionRepository projectionRepository;
    private final PermissionSetRepository permissionSetRepository;
    private final AppUserRepository userRepository;
    private final ProjectionMapper projectionMapper;
    private final PermissionSetMapper permissionSetMapper;
    private final AppUserMapper appUserMapper;

    public List<Projection> getAllProjections() {
        return projectionRepository.findAll();
    }

    public PermissionSet createPermissionSet(PermissionSet permissionSet) {
        return permissionSetRepository.save(permissionSet);
    }

    @Transactional
    public void grantProjectionsToPermissionSet(String permissionSetId, List<String> projectionIds) {
        if (projectionIds == null || projectionIds.isEmpty()) return;
        PermissionSet permissionSet = permissionSetRepository.findById(permissionSetId).orElseThrow(() -> new RuntimeException("PermissionSet not found: " + permissionSetId));
        List<Projection> projections = projectionRepository.findAllById(projectionIds);
        projections.forEach(permissionSet::addProjection);
        permissionSetRepository.save(permissionSet);
    }

    @Transactional
    public void revokeProjectionsFromPermissionSet(String permissionSetId, List<String> projectionIds) {
        if (projectionIds == null || projectionIds.isEmpty()) return;
        PermissionSet permissionSet = permissionSetRepository.findById(permissionSetId).orElseThrow(() -> new RuntimeException("PermissionSet not found: " + permissionSetId));
        List<Projection> projections = projectionRepository.findAllById(projectionIds);

        projections.forEach(permissionSet::removeProjection);
        permissionSetRepository.save(permissionSet);
    }

    @Transactional
    public void grantPermissionSetToUsers(List<String> usernames, String permissionSetId) {
        PermissionSet ps = permissionSetRepository.findById(permissionSetId).orElseThrow(() -> new RuntimeException("Permission Set not found"));
        List<AppUser> appUsers = userRepository.findAllById(usernames);
        appUsers.forEach(user -> user.addPermissionSet(ps));
        userRepository.saveAll(appUsers);
    }

    @Transactional
    public void revokePermissionSetToUsers(List<String> usernames, String permissionSetId) {
        PermissionSet ps = permissionSetRepository.findById(permissionSetId).orElseThrow(() -> new RuntimeException("Permission Set not found"));
        List<AppUser> appUsers = userRepository.findAllById(usernames);
        appUsers.forEach(user -> user.removePermissionSet(ps));
        userRepository.saveAll(appUsers);
    }

    @Transactional(readOnly = true)
    public Page<ProjectionDto> grantedProjections(String permissionSetId, String search, Pageable pageable) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";

        Page<Projection> projectionsPage = permissionSetRepository.findGrantedProjections(permissionSetId, searchTerm, pageable);

        return projectionsPage.map(projectionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ProjectionDto> getUnassignedProjections(String permissionSetId, String search, Pageable pageable) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";

        Page<Projection> unassignedProjections = permissionSetRepository.findUnassignedProjections(permissionSetId, searchTerm, pageable);

        return unassignedProjections.map(projectionMapper::toDto);
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

    @Transactional
    public List<AppUserDto> getGrantedUsers(String permissionSetId, String search) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";

        return appUserMapper.toDtoList(permissionSetRepository.findGrantedUsers(permissionSetId, searchTerm));

    }

    @Transactional
    public List<AppUserDto> getUnassignedUsers(String permissionSetId, String search) {
        String searchTerm = StringUtils.hasText(search) ? search.trim() : "";
        return appUserMapper.toDtoList(permissionSetRepository.findUnassignedUsers(permissionSetId, searchTerm));
    }
}