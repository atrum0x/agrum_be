package com.atrum.agrum.permission.mapper;

import com.atrum.agrum.permission.PermissionSet;
import com.atrum.agrum.permission.dto.PermissionSetDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.security.Permission;
import java.util.List;

@Mapper(componentModel = "spring")

public interface PermissionSetMapper {
    @Mapping(source = "id", target = "permissionSetId")
    PermissionSetDto toDto(PermissionSet permission);
    PermissionSet fromDto(PermissionSetDto permissionSetDto);

    List<PermissionSetDto> toDtoSet(List<PermissionSet> permissions);
}
