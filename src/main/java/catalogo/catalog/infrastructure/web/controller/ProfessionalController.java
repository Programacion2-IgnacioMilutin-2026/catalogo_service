package catalogo.catalog.infrastructure.web.controller;

import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.application.exception.ProfessionalNotFoundException;
import catalogo.catalog.application.service.ProfessionalService;
import catalogo.catalog.infrastructure.web.dto.ProfessionalPageResponse;
import catalogo.catalog.infrastructure.web.dto.ProfessionalResponse;
import catalogo.catalog.infrastructure.web.dto.ProfessionalSearchRequest;
import catalogo.catalog.infrastructure.web.dto.WholeNumber;
import catalogo.catalog.infrastructure.web.mapper.ProfessionalDtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Búsqueda pública de profesionales y consulta de uno con sus horarios.
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
            throw catalogNotInitialized();
        }
    }

    @GetMapping("/{professionalId}")
    public ResponseEntity<ProfessionalResponse> getProfessional(
            @PathVariable @WholeNumber(min = Long.MIN_VALUE, max = Long.MAX_VALUE, message = "must be a whole number")
            String professionalId) {
        try {
            return ResponseEntity.ok(professionalDtoMapper.toResponse(
                    professionalService.getProfessional(Long.valueOf(professionalId))));
        } catch (ProfessionalNotFoundException e) {
            ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "The professional is not in the catalog");
            notFound.getBody().setProperty("code", "PROFESSIONAL_NOT_FOUND");
            throw notFound;
        } catch (CatalogNotInitializedException e) {
            throw catalogNotInitialized();
        }
    }

    private static ResponseStatusException catalogNotInitialized() {
        ResponseStatusException notInitialized = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The catalog has not been initialized yet");
        notInitialized.getBody().setProperty("code", "CATALOG_NOT_INITIALIZED");
        return notInitialized;
    }
}
