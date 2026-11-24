package catalogo.catalog.infrastructure.web.controller;

import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.application.service.ProfessionalCategoryService;
import catalogo.catalog.infrastructure.web.dto.ProfessionalCategoryResponse;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalCategoryDtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

// Lista pública de las categorías de profesionales.
@RestController
@RequestMapping("/api/professional-categories")
@RequiredArgsConstructor
public class ProfessionalCategoryController {

    private final ProfessionalCategoryService professionalCategoryService;
    private final ProfessionalCategoryDtoMapper professionalCategoryDtoMapper;

    @GetMapping
    public ResponseEntity<List<ProfessionalCategoryResponse>> getAllProfessionalCategories() {
        try {
            List<ProfessionalCategoryResponse> categories = professionalCategoryService.getAllProfessionalCategories()
                    .stream()
                    .map(professionalCategoryDtoMapper::toResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(categories);
        } catch (CatalogNotInitializedException e) {
            ResponseStatusException notInitialized = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The catalog has not been initialized yet");
            notInitialized.getBody().setProperty("code", "CATALOG_NOT_INITIALIZED");
            throw notInitialized;
        }
    }
}
