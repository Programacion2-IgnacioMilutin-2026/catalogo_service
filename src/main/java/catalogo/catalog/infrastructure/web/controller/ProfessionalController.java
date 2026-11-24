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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

// Búsqueda pública de profesionales y consulta de uno con sus horarios.
@Slf4j
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
            String professionalId, Principal principal) {
        Long id = Long.valueOf(professionalId);
        String result = "failed";
        try {
            ProfessionalResponse response = professionalDtoMapper.toResponse(professionalService.getProfessional(id));
            result = "found with " + response.getWeeklySchedules().size() + " weekly schedules";
            return ResponseEntity.ok(response);
        } catch (ProfessionalNotFoundException e) {
            result = "not found";
            ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "The professional is not in the catalog");
            notFound.getBody().setProperty("code", "PROFESSIONAL_NOT_FOUND");
            throw notFound;
        } catch (CatalogNotInitializedException e) {
            result = "catalog not initialized";
            throw catalogNotInitialized();
        } finally {
            log.info("Professional {} requested by user {}: {}", id, principal.getName(), result);
        }
    }

    private static ResponseStatusException catalogNotInitialized() {
        ResponseStatusException notInitialized = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The catalog has not been initialized yet");
        notInitialized.getBody().setProperty("code", "CATALOG_NOT_INITIALIZED");
        return notInitialized;
    }
}
