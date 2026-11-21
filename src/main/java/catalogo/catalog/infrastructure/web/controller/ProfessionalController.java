package catalogo.catalog.infrastructure.web.controller;

import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.application.service.ProfessionalService;
import catalogo.catalog.infrastructure.web.dto.ProfessionalPageResponse;
import catalogo.catalog.infrastructure.web.dto.ProfessionalSearchRequest;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalDtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Búsqueda pública de profesionales.
@RestController
@RequestMapping("/api/professionals")
@RequiredArgsConstructor
public class ProfessionalController {

    private final ProfessionalService professionalService;
    private final ProfessionalDtoMapper professionalDtoMapper;

    @GetMapping
    public ResponseEntity<ProfessionalPageResponse> searchProfessionals(
            @Valid @ModelAttribute ProfessionalSearchRequest request) {
        int page = professionalDtoMapper.toPage(request);
        int size = professionalDtoMapper.toSize(request);
        try {
            return ResponseEntity.ok(professionalDtoMapper.toResponse(
                    professionalService.searchProfessionals(professionalDtoMapper.toDomain(request), page, size),
                    page, size));
        } catch (CatalogNotInitializedException e) {
            ResponseStatusException notInitialized = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The catalog has not been initialized yet");
            notInitialized.getBody().setProperty("code", "CATALOG_NOT_INITIALIZED");
            throw notInitialized;
        }
    }
}
