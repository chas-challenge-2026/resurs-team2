package se.comerit.resurs.api.v1.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotBlank;
import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.dto.DocumentResponse;
import se.comerit.resurs.exception.ApplicationNotFoundException;
import se.comerit.resurs.repository.ApplicationRepository;

@Service
public class BackofficeService {

    private final ApplicationRepository repository;

    public BackofficeService(ApplicationRepository repository) {
        this.repository = repository;
    }

    public @Nonnull ResponseEntity<ApplicationDetailsResponse> viewApplicationDetails(@Nonnull  Long id, @NotBlank String caseWorker) {
        return repository.findByIdWithDocuments(id).map(app -> new ApplicationDetailsResponse(
                new ApplicationResponse(
                        app.getId(),
                        app.getCompany().getName(),
                        app.getCompany().getOrgNumber(),
                        app.getRequestedAmount(),
                        app.getPurpose(),
                        app.getStatus(),
                        app.getDecision(),
                        app.getDecisionReason(),
                        app.getScoringResult(),
                        app.getCreatedAt(),
                        app.getUpdatedAt()),
                app.getAuditLog(),
                caseWorker,
                app.getDocuments().stream().map(document -> new DocumentResponse(
                        document.getId(),
                        document.getFilename(),
                        document.getDocType(),
                        document.getUploadedAt())).toList()))
                .map(ResponseEntity::ok).orElseThrow(() -> new ApplicationNotFoundException(id));
    }

}
