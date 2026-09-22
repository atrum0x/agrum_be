package com.atrum.agrum.division;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DivisionService {

    private final DivisionRepository divisionRepository;

    public DivisionService(DivisionRepository divisionRepository) {
        this.divisionRepository = divisionRepository;
    }

    // The Aspect intercepts this, checks the JWT, and enables the filter
    @Transactional(readOnly = true)
    public List<Division> getAllDivisions() {
        return divisionRepository.findAll(); // Translated to: SELECT * FROM divisions WHERE estate_id IN (...)
    }

    @Transactional
    public Division createDivision(Division division) {
        return divisionRepository.save(division);
    }
}