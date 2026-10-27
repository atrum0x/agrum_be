package com.atrum.agrum.mobile;

import com.atrum.agrum.attendance.AttendanceRepository;
import com.atrum.agrum.attendance.dto.AttendanceWorkerDto;
import com.atrum.agrum.collectionPoint.CollectionPointRepository;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.EmploymentStatus;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.mobile.dto.MobileCollectionPointDto;
import com.atrum.agrum.mobile.dto.MobileEstateDto;
import com.atrum.agrum.mobile.dto.MobileSetupDto;
import com.atrum.agrum.mobile.dto.MobileWorkTypeDto;
import com.atrum.agrum.security.PermissionChecker;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.WeighingService;
import com.atrum.agrum.workType.WorkTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileSetupService {

    private final UserService userService;
    private final PermissionChecker permissionChecker;
    private final EstateRepository estateRepository;
    private final WorkTypeRepository workTypeRepository;
    private final CollectionPointRepository collectionPointRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final WeighingService weighingService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MobileSetupDto getSetup() {
        AppUser user = userService.getCurrentUser();
        LocalDate today = LocalDate.now(clock);

        // Estate is not estate-filtered, so pick the user's sites here; the superuser sees all
        Collection<Estate> visible = permissionChecker.isSuperuser(user.getUsername())
                ? estateRepository.findAll()
                : user.getAllowedEstates();
        List<Estate> estates = visible.stream()
                .filter(estate -> Boolean.TRUE.equals(estate.getActive()))
                .sorted(Comparator.comparing(Estate::getId))
                .toList();

        List<MobileWorkTypeDto> workTypes = estates.stream()
                .flatMap(estate -> workTypeRepository.findAllByEstate(estate.getId()).stream())
                .map(wt -> new MobileWorkTypeDto(wt.getEstateId(), wt.getWorkType(), wt.isWeighingRequired()))
                .toList();
        List<MobileCollectionPointDto> collectionPoints = estates.stream()
                .flatMap(estate -> collectionPointRepository.findAllByEstate(estate.getId(), "").stream())
                .map(cp -> new MobileCollectionPointDto(cp.getEstateId(), cp.getCpCode(), cp.getName(), cp.isDefault()))
                .toList();

        List<AttendanceWorkerDto> workers = employeeRepository.findEmployedOn(EmploymentStatus.ACTIVE, today, "")
                .stream()
                .map(MobileSetupService::toWorker)
                .toList();

        Employee employee = user.getEmployee();
        return new MobileSetupDto(
                today,
                clock.instant(),
                employee == null ? null : idOf(employee.getDefaultEstate()),
                estates.stream().map(MobileSetupService::toEstate).toList(),
                workTypes,
                collectionPoints,
                workers,
                attendanceRepository.findAllEmployeeCodesRecordedOn(today).stream().distinct().sorted().toList(),
                weighingService.getWeighableWorkers(""));
    }

    private static MobileEstateDto toEstate(Estate estate) {
        return new MobileEstateDto(
                estate.getId(),
                estate.getName(),
                estate.getVariancePercent(),
                estate.getMaxDailyTransactions(),
                estate.getFullDayQtyKg(),
                estate.getHalfDayQtyKg());
    }

    private static AttendanceWorkerDto toWorker(Employee employee) {
        return new AttendanceWorkerDto(
                employee.getEmployeeCode(),
                employee.getFullName(),
                idOf(employee.getDefaultEstate()),
                idOf(employee.getHomeEstate()),
                employee.getDefaultCollectionPoint() == null ? null : employee.getDefaultCollectionPoint().getCpCode(),
                employee.getEmployeeCategory());
    }

    private static String idOf(Estate estate) {
        return estate == null ? null : estate.getId();
    }
}
