package se.comerit.resurs.service;

public class ScoringResult {
    private String scoringLog;
    private String decision;
    private String status;
    private String decisionReason;
    private int flagCount;
    private int kreditPoang;



    public String getScoringLog() { return scoringLog; }
    public void setScoringLog(String scoringLog) { this.scoringLog = scoringLog; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public int getFlagCount() { return flagCount; }
    public void setFlagCount(int flagCount) { this.flagCount = flagCount; }

    public int getKreditPoang() { return kreditPoang; }
    public void setKreditPoang(int kreditPoang) { this.kreditPoang = kreditPoang; }
}


