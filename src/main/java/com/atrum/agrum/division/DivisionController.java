package com.atrum.agrum.division;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/divisions")
public class DivisionController {

    private final DivisionService divisionService;

    public DivisionController(DivisionService divisionService) {
        this.divisionService = divisionService;
    }

    @GetMapping
    public ResponseEntity<List<Division>> getAllDivisions() {
        return ResponseEntity.ok(divisionService.getAllDivisions());
    }

    @PostMapping
    public ResponseEntity<Division> createDivision(@RequestBody Division division) {
        return ResponseEntity.ok(divisionService.createDivision(division));
    }
}