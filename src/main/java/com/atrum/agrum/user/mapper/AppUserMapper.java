package com.atrum.agrum.user.mapper;


import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.dto.AppUserDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AppUserMapper {
    AppUserDto toDto(AppUser appUser);
    AppUser fromDto(AppUserDto appUserDto);

    List<AppUserDto> toDtoList(List<AppUser> appUsers);
}
