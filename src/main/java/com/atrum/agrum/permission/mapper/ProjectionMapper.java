package com.atrum.agrum.permission.mapper;

import com.atrum.agrum.permission.dto.ProjectionDto;
import com.atrum.agrum.projection.Projection;

import java.util.Set;
import org.mapstruct.Mapper;

// componentModel = "spring" allows you to @Autowire this interface
@Mapper(componentModel = "spring")
public interface ProjectionMapper {

    ProjectionDto toDto(Projection projection);

    Set<ProjectionDto> toDtoSet(Set<Projection> projections);

    Projection toEntity(ProjectionDto projectionDto);
}