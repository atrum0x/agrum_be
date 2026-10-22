package com.atrum.agrum.estate.mapper;

import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.dto.EstateDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EstateMapper {
    EstateDto toDto(Estate estate);

    List<EstateDto> toDtoList(List<Estate> estates);
}
