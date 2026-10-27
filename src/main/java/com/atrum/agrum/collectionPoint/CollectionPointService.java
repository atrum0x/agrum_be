package com.atrum.agrum.collectionPoint;

import com.atrum.agrum.collectionPoint.dto.CollectionPointDto;
import com.atrum.agrum.collectionPoint.dto.CreateCollectionPointRequest;
import com.atrum.agrum.collectionPoint.dto.UpdateCollectionPointRequest;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.estate.SiteMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;

@Service
public class CollectionPointService {

    private static final int MAX_CODE_LENGTH = 30;

    private final CollectionPointRepository collectionPointRepository;
    private final EstateRepository estateRepository;

    public CollectionPointService(CollectionPointRepository collectionPointRepository,
                                  EstateRepository estateRepository) {
        this.collectionPointRepository = collectionPointRepository;
        this.estateRepository = estateRepository;
    }

    @Transactional(readOnly = true)
    public List<CollectionPointDto> getCollectionPoints(String estateId, String search) {
        // Postgres cannot type a null parameter in ":search IS NULL", so pass "" for no search
        String term = StringUtils.hasText(search) ? search.trim() : "";
        return collectionPointRepository.findAllByEstate(estateId, term).stream()
                .map(CollectionPointDto::from)
                .toList();
    }

    @Transactional
    public CollectionPointDto createCollectionPoint(String estateId, CreateCollectionPointRequest request) {
        if (!estateRepository.existsById(estateId)) {
            throw new IllegalArgumentException("Site not found: " + estateId);
        }
        // SB-09
        if (!StringUtils.hasText(request.cpCode())) {
            throw new IllegalArgumentException("Please enter the Collection Point.");
        }
        String cpCode = request.cpCode().trim();
        if (cpCode.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Collection Point must be at most " + MAX_CODE_LENGTH + " characters.");
        }
        // SB-09: unique within the site; save() would silently replace an existing one
        if (collectionPointRepository.existsById(new CollectionPointId(estateId, cpCode))) {
            throw new IllegalArgumentException(SiteMessages.SB_09_COLLECTION_POINT_EXISTS);
        }

        CollectionPoint collectionPoint = new CollectionPoint();
        collectionPoint.setCpCode(cpCode);
        collectionPoint.setEstateId(estateId);
        applyFields(collectionPoint, request.name(), request.description());
        // The first collection point of a site is always its default
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault())
                || !collectionPointRepository.existsInEstate(estateId);
        collectionPoint.setDefault(makeDefault);
        collectionPoint = collectionPointRepository.save(collectionPoint);
        if (makeDefault) {
            collectionPointRepository.clearDefaultExcept(estateId, cpCode);
        }
        return CollectionPointDto.from(collectionPoint);
    }

    @Transactional
    public CollectionPointDto updateCollectionPoint(String estateId, String cpCode, UpdateCollectionPointRequest request) {
        CollectionPoint collectionPoint = findInEstate(estateId, cpCode);
        applyFields(collectionPoint, request.name(), request.description());
        if (request.isDefault() != null && request.isDefault() != collectionPoint.isDefault()) {
            if (!request.isDefault()) {
                throw new IllegalArgumentException(
                        "A site needs a default collection point. Make another collection point the default instead.");
            }
            collectionPointRepository.save(collectionPoint);
            return setDefault(estateId, cpCode);
        }
        return CollectionPointDto.from(collectionPointRepository.save(collectionPoint));
    }

    @Transactional
    public CollectionPointDto setDefault(String estateId, String cpCode) {
        CollectionPoint collectionPoint = findInEstate(estateId, cpCode);
        collectionPoint.setDefault(true);
        collectionPointRepository.save(collectionPoint);
        // Bulk update flushes first and clears the persistence context, so re-read afterwards
        collectionPointRepository.clearDefaultExcept(estateId, cpCode);
        return CollectionPointDto.from(findInEstate(estateId, cpCode));
    }

    @Transactional
    public void deleteCollectionPoints(String estateId, List<String> cpCodes) {
        if (cpCodes == null || cpCodes.isEmpty()) {
            throw new IllegalArgumentException("Select at least one collection point to delete");
        }
        List<CollectionPoint> collectionPoints = collectionPointRepository.findAllInEstate(estateId, cpCodes);
        if (collectionPoints.size() != new HashSet<>(cpCodes).size()) {
            throw new IllegalArgumentException("Some collection points were not found in site " + estateId);
        }
        // employees.default_collection_point_code references these rows
        List<String> employeeCodes = collectionPointRepository.findEmployeesUsingAsDefault(estateId, cpCodes);
        if (!employeeCodes.isEmpty()) {
            throw new IllegalArgumentException("Collection point is the default for employees: "
                    + String.join(", ", employeeCodes.stream().limit(5).toList())
                    + (employeeCodes.size() > 5 ? " and " + (employeeCodes.size() - 5) + " more" : ""));
        }
        // SB-12
        if (collectionPointRepository.isUsedInAttendance(estateId, cpCodes)) {
            throw new IllegalArgumentException(SiteMessages.SB_12_IN_USE);
        }
        boolean deletesDefault = collectionPoints.stream().anyMatch(CollectionPoint::isDefault);
        collectionPointRepository.deleteAll(collectionPoints);
        collectionPointRepository.flush();

        // Keep exactly one default: promote the first remaining collection point
        if (deletesDefault) {
            collectionPointRepository.findAllByEstate(estateId, "").stream().findFirst()
                    .ifPresent(next -> setDefault(estateId, next.getCpCode()));
        }
    }

    private CollectionPoint findInEstate(String estateId, String cpCode) {
        return collectionPointRepository.findInEstate(estateId, cpCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Collection point " + cpCode + " not found in site " + estateId));
    }

    private void applyFields(CollectionPoint collectionPoint, String name, String description) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Name is required");
        }
        collectionPoint.setName(name.trim());
        collectionPoint.setDescription(StringUtils.hasText(description) ? description.trim() : null);
    }
}
