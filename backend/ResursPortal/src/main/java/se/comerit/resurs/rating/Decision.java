package se.comerit.resurs.rating;

/**
 * Decision
 */
public enum Decision {
    APPROVED,
    UNDER_REVIEW,
    REJECTED;

    /**
     * Legacy decision column value ({@code REVIEW} for manual review).
     */
    public String decisionValue() {
        return this == UNDER_REVIEW ? "REVIEW" : name();
    }

    /**
     * Legacy status column value.
     */
    public String statusValue() {
        return name();
    }
}
