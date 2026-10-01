package com.atrum.agrum.projection.mapper;

import com.atrum.agrum.projection.dto.ProjectionDto;

import java.util.Set;

import com.atrum.agrum.projection.Projection;
import org.mapstruct.Mapper;

// componentModel = "spring" allows you to @Autowire this interface
@Mapper(componentModel = "spring")
public interface ProjectionMapper {

    ProjectionDto toDto(Projection projection);

    Set<ProjectionDto> toDtoSet(Set<Projection> projections);

    Projection toEntity(ProjectionDto projectionDto);
}