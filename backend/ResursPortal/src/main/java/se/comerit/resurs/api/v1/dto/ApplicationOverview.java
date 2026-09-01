package se.comerit.resurs.api.v1.dto;

import java.util.List;

import jakarta.annotation.Nonnull;

public record ApplicationOverview(
        @Nonnull List<ApplicationResponse> reviewApplications,
        @Nonnull List<ApplicationResponse> decidedApplications,
        @Nonnull String workerName, @Nonnull int reviewCount) {

}
