package com.atrum.agrum.permission.dto;

import java.util.Date;

public record PermissionSetDto(
     String permissionSetId,
     String description,
     Date modified,
     String modifiedBy
) {}
