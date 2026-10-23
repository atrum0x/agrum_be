package com.atrum.agrum.collectionPoint;

import com.atrum.agrum.collectionPoint.dto.CollectionPointDto;
import com.atrum.agrum.collectionPoint.dto.CreateCollectionPointRequest;
import com.atrum.agrum.collectionPoint.dto.UpdateCollectionPointRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/estates/{estateId}/collection-points")
public class CollectionPointController {

    private final CollectionPointService collectionPointService;

    public CollectionPointController(CollectionPointService collectionPointService) {
        this.collectionPointService = collectionPointService;
    }

    @GetMapping
    public ResponseEntity<List<CollectionPointDto>> getCollectionPoints(
            @PathVariable String estateId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(collectionPointService.getCollectionPoints(estateId, search));
    }

    @PostMapping
    public ResponseEntity<CollectionPointDto> createCollectionPoint(
            @PathVariable String estateId,
            @RequestBody CreateCollectionPointRequest request) {
        return ResponseEntity.ok(collectionPointService.createCollectionPoint(estateId, request));
    }

    @PutMapping("/{cpCode}")
    public ResponseEntity<CollectionPointDto> updateCollectionPoint(
            @PathVariable String estateId,
            @PathVariable String cpCode,
            @RequestBody UpdateCollectionPointRequest request) {
        return ResponseEntity.ok(collectionPointService.updateCollectionPoint(estateId, cpCode, request));
    }

    @PostMapping("/delete")
    public ResponseEntity<String> deleteCollectionPoints(
            @PathVariable String estateId,
            @RequestBody List<String> cpCodes) {
        collectionPointService.deleteCollectionPoints(estateId, cpCodes);
        return ResponseEntity.ok("Collection points deleted from site " + estateId);
    }

    @PutMapping("/{cpCode}/default")
    public ResponseEntity<CollectionPointDto> setDefaultCollectionPoint(
            @PathVariable String estateId,
            @PathVariable String cpCode) {
        return ResponseEntity.ok(collectionPointService.setDefault(estateId, cpCode));
    }
}
