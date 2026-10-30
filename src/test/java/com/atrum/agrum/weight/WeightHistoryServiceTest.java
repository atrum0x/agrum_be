package com.atrum.agrum.weight;

import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.WeightHistorySearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeightHistoryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    @Mock
    private WeightRecordRepository weightRecordRepository;
    @Mock
    private WeightChangeRequestRepository weightChangeRequestRepository;
    @Mock
    private WeightPhotoRepository weightPhotoRepository;
    @Mock
    private UserService userService;

    private WeightHistoryService weightHistoryService;
    private AppUser picker;

    @BeforeEach
    void setUp() {
        weightHistoryService = new WeightHistoryService(weightRecordRepository, weightChangeRequestRepository, weightPhotoRepository,
                userService, Clock.fixed(Instant.parse("2026-10-24T04:30:00Z"), ZoneId.of("Asia/Colombo")));
        Employee employee = new Employee();
        employee.setEmployeeCode("E001");
        picker = new AppUser();
        picker.setUsername("E001");
        picker.setEmployee(employee);
        when(userService.getCurrentUser()).thenReturn(picker);
        when(weightRecordRepository.search(any(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
    }

    @Test
    void TC68_pickerSearch_isLimitedToTheirOwnEmployeeCode() {
        weightHistoryService.searchMine(search(null), PageRequest.of(0, 50));

        verify(weightRecordRepository).search(eq(TODAY), eq(TODAY), eq("E001"), eq(""), eq(""), eq(""),
                eq(""), any(), any(), any(), any(), any());
    }

    @Test
    void TC68_pickerCannotSearchAnotherWorker() {
        weightHistoryService.searchMine(search("E002"), PageRequest.of(0, 50));

        verify(weightRecordRepository).search(any(), any(), eq("E001"), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void loginWithoutEmployee_isRejected() {
        picker.setEmployee(null);

        assertThatThrownBy(() -> weightHistoryService.searchMine(search(null), PageRequest.of(0, 50)))
                .hasMessage("Your login is not linked to an employee.");
    }

    @Test
    void WH01_dateFromAfterDateTo_isRejected() {
        WeightHistorySearchRequest request = new WeightHistorySearchRequest(TODAY.plusDays(1), TODAY,
                null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> weightHistoryService.search(request, PageRequest.of(0, 50)))
                .hasMessage(WeightMessages.WH_01_DATES);
    }

    private static WeightHistorySearchRequest search(String employeeCode) {
        return new WeightHistorySearchRequest(null, null, employeeCode,
                null, null, null, null, null, null, null, null);
    }
}
