package com.atrum.agrum.dashboard;

import com.atrum.agrum.dashboard.dto.DashboardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard (FSD 2.2). Pickers are granted /mine instead of the full counts. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardDto> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }

    @GetMapping("/mine")
    public ResponseEntity<DashboardDto> getMyDashboard() {
        return ResponseEntity.ok(dashboardService.getMyDashboard());
    }
}
