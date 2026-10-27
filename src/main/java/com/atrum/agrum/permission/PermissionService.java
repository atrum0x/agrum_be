package com.atrum.agrum.permission;

import com.atrum.agrum.exception.ResourceNotFoundException;
import com.atrum.agrum.permission.dto.CreatePermissionSetRequest;
import com.atrum.agrum.permission.dto.PermissionSetDto;
import com.atrum.agrum.permission.dto.UpdatePermissionSetRequest;
import com.atrum.agrum.projection.dto.ProjectionDto;
import com.atrum.agrum.permission.mapper.PermissionSetMapper;
import com.atrum.agrum.projection.mapper.ProjectionMapper;
import com.atrum.agrum.projection.Projection;
import com.atrum.agrum.projection.ProjectionRepository;
import com.atrum.agrum.security.PermissionCacheEvictor;
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
    private final PermissionCacheEvictor permissionCacheEvictor;

    public List<Projection> getAllProjections() {
        return projectionRepository.findAll();
    }

    @Transactional
    public PermissionSetDto createPermissionSet(CreatePermissionSetRequest request) {
        if (!StringUtils.hasText(request.permissionSetId())) {
            throw new IllegalArgumentException("Permission set ID is required");
        }
        String permissionSetId = request.permissionSetId().trim();
        // save() would silently replace an existing set and drop its grants
        if (permissionSetRepository.existsById(permissionSetId)) {
            throw new IllegalArgumentException("Permission set already exists: " + permissionSetId);
        }

        PermissionSet permissionSet = new PermissionSet();
        permissionSet.setId(permissionSetId);
        permissionSet.setDescription(request.description());
        return permissionSetMapper.toDto(permissionSetRepository.save(permissionSet));
    }

    @Transactional
    public PermissionSetDto updatePermissionSet(String permissionSetId, UpdatePermissionSetRequest request) {
        PermissionSet permissionSet = permissionSetRepository.findById(permissionSetId)
                .orElseThrow(() -> new ResourceNotFoundException("PermissionSet not found: " + permissionSetId));
        permissionSet.setDescription(request.description());
        return permissionSetMapper.toDto(permissionSetRepository.save(permissionSet));
    }

    @Transactional
    public void grantProjectionsToPermissionSet(String permissionSetId, List<String> projectionIds) {
        if (projectionIds == null || projectionIds.isEmpty()) return;
        PermissionSet permissionSet = permissionSetRepository.findById(permissionSetId).orElseThrow(() -> new RuntimeException("PermissionSet not found: " + permissionSetId));
        List<Projection> projections = projectionRepository.findAllById(projectionIds);
        projections.forEach(permissionSet::addProjection);
        permissionSetRepository.save(permissionSet);
        permissionCacheEvictor.evictAll();
    }

    @Transactional
    public void revokeProjectionsFromPermissionSet(String permissionSetId, List<String> projectionIds) {
        if (projectionIds == null || projectionIds.isEmpty()) return;
        PermissionSet permissionSet = permissionSetRepository.findById(permissionSetId).orElseThrow(() -> new RuntimeException("PermissionSet not found: " + permissionSetId));
        List<Projection> projections = projectionRepository.findAllById(projectionIds);

        projections.forEach(permissionSet::removeProjection);
        permissionSetRepository.save(permissionSet);
        permissionCacheEvictor.evictAll();
    }

    @Transactional
    public void grantPermissionSetToUsers(List<String> usernames, String permissionSetId) {
        PermissionSet ps = permissionSetRepository.findById(permissionSetId).orElseThrow(() -> new RuntimeException("Permission Set not found"));
        List<AppUser> appUsers = userRepository.findAllById(usernames);
        appUsers.forEach(user -> user.addPermissionSet(ps));
        userRepository.saveAll(appUsers);
        permissionCacheEvictor.evictUsers(appUsers.stream().map(AppUser::getUsername).toList());
    }

    @Transactional
    public void revokePermissionSetToUsers(List<String> usernames, String permissionSetId) {
        PermissionSet ps = permissionSetRepository.findById(permissionSetId).orElseThrow(() -> new RuntimeException("Permission Set not found"));
        List<AppUser> appUsers = userRepository.findAllById(usernames);
        appUsers.forEach(user -> user.removePermissionSet(ps));
        userRepository.saveAll(appUsers);
        permissionCacheEvictor.evictUsers(appUsers.stream().map(AppUser::getUsername).toList());
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