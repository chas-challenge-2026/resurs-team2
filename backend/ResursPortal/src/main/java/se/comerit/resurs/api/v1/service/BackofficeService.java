package se.comerit.resurs.api.v1.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotBlank;
import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.mapper.ApplicationMapper;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;

@Service
public class BackofficeService {

    private final ApplicationRepository repository;

    public BackofficeService(ApplicationRepository repository) {
        this.repository = repository;
    }

    public @Nonnull ResponseEntity<ApplicationDetailsResponse> viewApplicationDetails(@Nonnull  Long id, @NotBlank String caseWorker) {
        return repository.findByIdWithDocuments(id)
                .map(app -> ApplicationMapper.toDetailsResponse(app, caseWorker))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
    }

}
