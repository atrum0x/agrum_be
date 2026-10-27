package com.atrum.agrum.mobile;

import com.atrum.agrum.mobile.dto.MobileSetupDto;
import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ProjectionGroup("Mobile App")
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
public class MobileSetupController {

    private final MobileSetupService mobileSetupService;

    @Operation(summary = "Download the sites, work types, collection points, workers and today's weighable attendance the phone keeps for working offline. Give to field supervisors")
    @GetMapping("/setup")
    public ResponseEntity<MobileSetupDto> getSetup() {
        return ResponseEntity.ok(mobileSetupService.getSetup());
    }
}
