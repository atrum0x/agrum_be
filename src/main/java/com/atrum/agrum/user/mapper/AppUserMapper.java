package com.atrum.agrum.user.mapper;


import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.dto.AppUserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AppUserMapper {
    @Mapping(target = "employeeCode", source = "employee.employeeCode")
    AppUserDto toDto(AppUser appUser);

    List<AppUserDto> toDtoList(List<AppUser> appUsers);
}
