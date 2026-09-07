package se.comerit.resurs.api.v1.mapper;

import java.util.List;

import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.dto.ApplicationRequest;
import se.comerit.resurs.api.v1.dto.ApplicationResponse;
import se.comerit.resurs.api.v1.dto.DocumentResponse;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Decision;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.rating.ScoringResult;

public final class ApplicationMapper {

    private ApplicationMapper() {
    }

    /**
     * Derives the application status from the scoring decision.
     */
    public static ApplicationStatus toStatus(ScoringResult score) {
        return switch (score.decision()) {
            case APPROVED -> ApplicationStatus.APPROVED;
            case REJECTED -> ApplicationStatus.REJECTED;
            case UNDER_REVIEW -> ApplicationStatus.UNDER_REVIEW;
        };
    }

    /**
     * Derives the application status from a manual decision.
     */
    public static ApplicationStatus toStatus(Decision decision) {
        return switch (decision) {
            case APPROVED -> ApplicationStatus.APPROVED;
            case REJECTED -> ApplicationStatus.REJECTED;
        };
    }

    /**
     * Derives the persisted decision from the scoring decision. A manual
     * review decision has no persisted decision value ({@code null}).
     */
    public static Decision toDecision(ScoringResult score) {
        return switch (score.decision()) {
            case APPROVED -> Decision.APPROVED;
            case REJECTED -> Decision.REJECTED;
            case UNDER_REVIEW -> null;
        };
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

    public static ApplicationData toApplicationData(ApplicationRequest request) {
        return new ApplicationData(
                request.equity(),
                request.totalCapital(),
                request.currentAssets(),
                request.currentLiabilities(),
                request.totalLiabilities(),
                request.operatingIncome(),
                request.netRevenue(),
                request.requestedAmount(),
                valueOrZero(request.operatingCashFlow()),
                valueOrZero(request.investingCashFlow()),
                valueOrZero(request.interestExpenses()),
                request.industry() != null ? request.industry() : "");
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
