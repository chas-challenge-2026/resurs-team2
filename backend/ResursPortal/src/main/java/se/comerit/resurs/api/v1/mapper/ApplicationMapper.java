package se.comerit.resurs.api.v1.mapper;

import java.util.List;

import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationOverview;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.dto.DocumentResponse;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.Document;

public final class ApplicationMapper {

    private ApplicationMapper() {
    }

    public static ApplicationDetailsResponse toDetailsResponse(Application app, String caseWorker) {
        return new ApplicationDetailsResponse(
                toResponse(app),
                app.getAuditLog(),
                caseWorker,
                app.getDocuments().stream().map(ApplicationMapper::toDocumentResponse).toList());
    }

    public static ApplicationResponse toResponse(Application app) {
        return new ApplicationResponse(
                app.getId(),
                app.getCompany().getName(),
                app.getCompany().getOrgNumber(),
                app.getRequestedAmount(),
                app.getPurpose(),
                app.getStatus(),
                app.getDecision() != null ? app.getDecision().name() : null,
                app.getDecisionReason(),
                app.getScoringResult(),
                app.getCreatedAt(),
                app.getUpdatedAt());
    }

    public static DocumentResponse toDocumentResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getDocType(),
                document.getUploadedAt());
    }

    public static ApplicationOverview toApplicationOverview(List<Application> reviewApplications,
            List<Application> decidedApplications, String workerName) {
        return new ApplicationOverview(
                reviewApplications.stream().map(ApplicationMapper::toResponse).toList(),
                decidedApplications.stream().map(ApplicationMapper::toResponse).toList(),
                workerName, reviewApplications.size());
    }
}
