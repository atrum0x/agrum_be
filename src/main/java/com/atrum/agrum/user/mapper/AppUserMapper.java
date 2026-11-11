package com.atrum.agrum.user.mapper;


import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.dto.AppUserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AppUserMapper {
    @Mapping(target = "employeeCode", source = "employee.employeeCode")
    @Mapping(target = "employeeName", expression = "java(employeeName(appUser))")
    AppUserDto toDto(AppUser appUser);

    // "First Last" of the linked employee, so pickers can show code - name
    default String employeeName(AppUser appUser) {
        if (appUser.getEmployee() == null) return null;
        String first = appUser.getEmployee().getFirstName();
        String last = appUser.getEmployee().getLastName();
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return name.isEmpty() ? null : name;
    }

    List<AppUserDto> toDtoList(List<AppUser> appUsers);
}
