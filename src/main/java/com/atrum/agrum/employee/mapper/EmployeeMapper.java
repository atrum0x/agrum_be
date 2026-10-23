package com.atrum.agrum.employee.mapper;

import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.dto.CreateEmployeeRequest;
import com.atrum.agrum.employee.dto.EmployeeDto;
import com.atrum.agrum.employee.dto.UpdateEmployeeRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "defaultEstateId", source = "defaultEstate.id")
    @Mapping(target = "defaultCollectionPointCode", source = "defaultCollectionPoint.cpCode")
    @Mapping(target = "homeEstateId", source = "homeEstate.id")
    @Mapping(target = "hasUserAccount", expression = "java(employee.getUserAccount() != null)")
    EmployeeDto toDto(Employee employee);

    @Mapping(target = "defaultEstate", ignore = true)
    @Mapping(target = "defaultCollectionPoint", ignore = true)
    @Mapping(target = "homeEstate", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Employee fromCreateRequest(CreateEmployeeRequest request);

    @Mapping(target = "defaultEstate", ignore = true)
    @Mapping(target = "defaultCollectionPoint", ignore = true)
    @Mapping(target = "homeEstate", ignore = true)
    @Mapping(target = "employeeCode", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEmployee(UpdateEmployeeRequest request, @MappingTarget Employee employee);
}
