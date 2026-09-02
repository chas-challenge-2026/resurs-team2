package se.comerit.resurs.controller;

import se.comerit.resurs.api.v1.service.ScoringService;
import se.comerit.resurs.dto.ScoringInput;
import se.comerit.resurs.rating.ApplicationData;
import se.comerit.resurs.rating.Score;
import se.comerit.resurs.rating.ScoringResult;
import se.comerit.resurs.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


import jakarta.servlet.http.HttpSession;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.Map;

/**
 * ApplicationController – Hanterar kreditansökningar.
 *
 * VARNING: Denna klass innehåller avsiktliga anti-patterns för pedagogiskt syfte.
 * Se docs/known-bugs.md för fullständig lista.
 *
 * Anti-patterns inkluderar:
 *  - JdbcTemplate direkt i kontrollern (ingen service/repository-lager)
 *  - Inline scoring-logik (800+ rader i en metod)
 *  - Audit log som JSON-blob i en kolumn
 *  - Ingen transaktion vid ansökningsskapande
 *  - PII i klartext
 *  - Session-check copy-pasteat i varje metod
 *  - Magic numbers spridda i scoring-logiken
 */
@Controller("legacyApplication")
public class ApplicationController {

    private final ScoringService scoringService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public ApplicationController(ScoringService scoringService) {
        this.scoringService = scoringService;

    }

    // ============================================================
    // GET /apply — visa ansökningsformulär
    // ============================================================
    @GetMapping("/apply")
    public String showApplyForm(HttpSession session, Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null) return "redirect:/login";
        if (!"company".equals(session.getAttribute("role"))) return "redirect:/login";

        model.addAttribute("companyName", session.getAttribute("companyName"));
        model.addAttribute("orgNumber", session.getAttribute("orgNumber"));
        return "apply";
    }

    // ============================================================
    // POST /apply — skapa ansökan + kör scoring inline
    // ============================================================
    @PostMapping("/apply")
    public String submitApplication(
            @RequestParam("orgNumber") String orgNumber,
            @RequestParam("companyName") String companyName,
            @RequestParam("authorizedSignatory") String authorizedSignatory,
            @RequestParam("egetKapital") double egetKapital,
            @RequestParam("totaltKapital") double totaltKapital,
            @RequestParam("omsattningstillgangar") double omsattningstillgangar,
            @RequestParam("kortfristigaSkulder") double kortfristigaSkulder,
            @RequestParam("totalaSkulder") double totalaSkulder,
            @RequestParam("rorelseresultat") double rorelseresultat,
            @RequestParam("nettoomsattning") double nettoomsattning,
            @RequestParam("requestedAmount") BigDecimal requestedAmount,
            @RequestParam("purpose") String purpose,
            @RequestParam(value = "operativtKassaflode", defaultValue = "") double operativtKassaflode,
            @RequestParam(value = "investeringsKassaflode", defaultValue = "") double investeringsKassaflode,
            @RequestParam(value = "ranteKostnader", defaultValue = "") double ranteKostnader,
            @RequestParam(value = "bransch", defaultValue = "") String bransch,
            HttpSession session,
            Model model) {

        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null) return "redirect:/login";
        if (!"company".equals(session.getAttribute("role"))) return "redirect:/login";


        // ===========================================================
        // SCORING — delegerad till ScoringService
        // ===========================================================
        ApplicationData data = new ApplicationData(
            egetKapital,
            totaltKapital,
            omsattningstillgangar,
            kortfristigaSkulder,
            totalaSkulder,
            rorelseresultat,
            nettoomsattning,
            requestedAmount,
            operativtKassaflode,
            investeringsKassaflode,
            ranteKostnader,
            bransch
        );

        Score scoring = ScoringService.toScore(scoringService.score(data));
        String decision = scoring.decision();
        int flagCount = scoring.flagCount();
        String scoringLog = scoring.scoringLog();
        String status = scoring.status();
        String decisionReason = scoring.decisionReason();
        // ===========================================================
        // INSERT 1: Upsert company (no ON CONFLICT — just check first)
        // No transaction — three separate INSERTs follow
        // TODO: wrap in @Transactional
        // ===========================================================
        List<Map<String, Object>> existingCompany = jdbcTemplate.queryForList(
                "SELECT id FROM companies WHERE org_number = '" + orgNumber + "'"
        );

        long companyId;
        if (existingCompany.isEmpty()) {
            // INSERT company — PII in plaintext, no encryption
            // TODO: encrypt PII before go-live
            KeyHolder companyKeyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO companies (org_number, company_name, authorized_signatory) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, orgNumber);
                ps.setString(2, companyName);
                ps.setString(3, authorizedSignatory);
                return ps;
            }, companyKeyHolder);
            companyId = companyKeyHolder.getKey().longValue();
        } else {
            companyId = ((Number) existingCompany.get(0).get("id")).longValue();
        }

        session.setAttribute("companyId", companyId);


        // ===========================================================
        // INSERT 2: Skapa ansökan — ingen transaktion, tre separata INSERTs
        // TODO: wrap in @Transactional
        // ===========================================================
        String initialAuditLog = "[{\"ts\":\"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "\",\"action\":\"APPLICATION_CREATED\",\"orgNumber\":\"" + orgNumber + "\"}]";

        KeyHolder appKeyHolder = new GeneratedKeyHolder();
        final long finalCompanyId = companyId;
        final String finalScoringLog = scoringLog;
        final String finalDecision = decision;
        final String finalStatus = status;
        final String finalDecisionReason = decisionReason;
        final String finalAuditLog = initialAuditLog;
        final BigDecimal finalAmount = requestedAmount;

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO applications (company_id, requested_amount, purpose, status, decision, decision_reason, scoring_result, audit_log) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, finalCompanyId);
            ps.setBigDecimal(2, finalAmount);
            ps.setString(3, purpose);
            ps.setString(4, finalStatus);
            ps.setString(5, finalDecision.equals("REVIEW") ? null : finalDecision);
            ps.setString(6, finalDecisionReason);
            ps.setString(7, finalScoringLog);
            ps.setString(8, finalAuditLog);
            return ps;
        }, appKeyHolder);

        long applicationId = appKeyHolder.getKey().longValue();

        // ===========================================================
        // INSERT 3: Uppdatera audit log med scoring-resultat
        // Hämtar blob, deserialiserar, lägger till, re-serialiserar
        // Ingen index, ingen separat tabell — allt i en JSON-blob
        // TODO: skapa separat audit_log-tabell med index
        // ===========================================================
        String scoringAuditEntry = "{\"ts\":\"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "\",\"action\":\"SCORING_RUN\",\"result\":\"" + decision + "\",\"flags\":" + flagCount + "}";

        // Fetch current audit log blob
        String currentAuditLog = jdbcTemplate.queryForObject(
                "SELECT audit_log FROM applications WHERE id = ?",
                String.class,
                applicationId
        );

        // Append new entry — string manipulation on JSON blob, no proper JSON library
        String updatedAuditLog;
        if (currentAuditLog == null || currentAuditLog.equals("[]")) {
            updatedAuditLog = "[" + scoringAuditEntry + "]";
        } else {
            // Strip trailing ] and append
            updatedAuditLog = currentAuditLog.substring(0, currentAuditLog.lastIndexOf("]"))
                + "," + scoringAuditEntry + "]";
        }

        jdbcTemplate.update(
            "UPDATE applications SET audit_log = ?, updated_at = NOW() WHERE id = ?",
            updatedAuditLog,
            applicationId
        );
        // End of INSERT 3 — still no transaction around all three operations

        return "redirect:/application/" + applicationId;
    }

    // ============================================================
    // GET /application/{id} — visa enskild ansökan
    // ============================================================
    @GetMapping("/application/{id}")
    public String viewApplication(@PathVariable("id") Long id,
                                  HttpSession session,
                                  Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null) return "redirect:/login";

        String role = (String) session.getAttribute("role");

        List<Map<String, Object>> apps;
        if ("caseWorker".equals(role)) {
            apps = jdbcTemplate.queryForList(
                "SELECT a.*, c.org_number, c.company_name, c.authorized_signatory " +
                "FROM applications a JOIN companies c ON a.company_id = c.id " +
                "WHERE a.id = ?", id
            );
        } else {
            // Company can only see their own applications
            Long companyId = (Long) session.getAttribute("companyId");
            if (companyId == null) {
                // Try to find companyId from orgNumber
                String orgNumber = (String) session.getAttribute("orgNumber");
                List<Map<String, Object>> cRows = jdbcTemplate.queryForList(
                    "SELECT id FROM companies WHERE org_number = ?", orgNumber
                );
                if (cRows.isEmpty()) return "redirect:/apply";
                companyId = ((Number) cRows.get(0).get("id")).longValue();
                session.setAttribute("companyId", companyId);
            }
            apps = jdbcTemplate.queryForList(
                "SELECT a.*, c.org_number, c.company_name, c.authorized_signatory " +
                "FROM applications a JOIN companies c ON a.company_id = c.id " +
                "WHERE a.id = ? AND a.company_id = ?", id, companyId
            );
        }

        if (apps.isEmpty()) {
            model.addAttribute("error", "Ansökan hittades inte.");
            return "redirect:/applications";
        }

        Map<String, Object> app = apps.get(0);
        model.addAttribute("application", app);
        model.addAttribute("role", role);

        // Parse audit log — manual JSON string splitting, no proper parser
        String auditLogBlob = (String) app.get("audit_log");
        model.addAttribute("auditLogRaw", auditLogBlob);

        // Fetch documents for this application
        List<Map<String, Object>> docs = jdbcTemplate.queryForList(
            "SELECT * FROM documents WHERE application_id = ?", id
        );
        model.addAttribute("documents", docs);

        return "status";
    }

    // ============================================================
    // GET /applications — lista alla ansökningar för företaget
    // ============================================================
    @GetMapping("/applications")
    public String listApplications(HttpSession session, Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null) return "redirect:/login";
        if (!"company".equals(session.getAttribute("role"))) return "redirect:/login";

        String orgNumber = (String) session.getAttribute("orgNumber");

        // Get companyId via orgNumber — no caching, hits DB every time
        List<Map<String, Object>> companyRows = jdbcTemplate.queryForList(
            "SELECT id FROM companies WHERE org_number = '" + orgNumber + "'"
        );

        if (companyRows.isEmpty()) {
            model.addAttribute("applications", java.util.Collections.emptyList());
            return "applications";
        }

        long companyId = ((Number) companyRows.get(0).get("id")).longValue();

        List<Map<String, Object>> apps = jdbcTemplate.queryForList(
            "SELECT a.id, a.requested_amount, a.purpose, a.status, a.decision, a.created_at, a.updated_at " +
            "FROM applications a WHERE a.company_id = ? ORDER BY a.created_at DESC",
            companyId
        );

        model.addAttribute("applications", apps);
        model.addAttribute("companyName", session.getAttribute("companyName"));
        return "applications";
    }

    // ============================================================
    // GET /dashboard — startsida för inloggad företagsanvändare
    // ============================================================
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null) return "redirect:/login";
        if (!"company".equals(session.getAttribute("role"))) return "redirect:/backoffice";

        String orgNumber = (String) session.getAttribute("orgNumber");

        List<Map<String, Object>> companyRows = jdbcTemplate.queryForList(
            "SELECT id FROM companies WHERE org_number = '" + orgNumber + "'"
        );

        if (companyRows.isEmpty()) {
            model.addAttribute("applications", java.util.Collections.emptyList());
            model.addAttribute("companyName", session.getAttribute("companyName"));
            return "dashboard";
        }

        long companyId = ((Number) companyRows.get(0).get("id")).longValue();

        // Count applications by status
        List<Map<String, Object>> apps = jdbcTemplate.queryForList(
            "SELECT a.id, a.requested_amount, a.purpose, a.status, a.decision, a.created_at " +
            "FROM applications a WHERE a.company_id = ? ORDER BY a.created_at DESC LIMIT 5",
            companyId
        );

        model.addAttribute("applications", apps);
        model.addAttribute("companyName", session.getAttribute("companyName"));
        return "dashboard";
    }

    // ============================================================
    // Helper: formatera status som svensk text
    // Duplicerad logik — finns också i Thymeleaf-template
    // TODO: använd en enumklass
    // ============================================================
    private String statusToSwedish(String status) {
        if (status == null) return "Okänd";
        switch (status) {
            case "PENDING_DOCS": return "Väntar på dokument";
            case "UNDER_REVIEW": return "Under granskning";
            case "APPROVED": return "Godkänd";
            case "REJECTED": return "Avslagen";
            default: return status;
        }
    }

    // ============================================================
    // Helper: bygg scoring-sammanfattning (inline, ingen service)
    // Duplicerar logik från POST /apply — TODO: extrahera till service
    // ============================================================
    private String buildScoringExplanation(String scoringResult) {
        if (scoringResult == null || scoringResult.isEmpty()) {
            return "Ingen scoring tillgänglig.";
        }
        // Just return the raw string — no structured parsing
        // TODO: parse properly and present user-friendly explanation
        return scoringResult;
    }

    // ============================================================
    // Unused leftover from early development — never removed
    // TODO: ta bort eller flytta till en util-klass
    // ============================================================
    @Deprecated
    private double calculateDebtRatio(double totalSkulder, double egetKapital) {
        if (egetKapital == 0) return Double.MAX_VALUE;
        return totalSkulder / egetKapital;
    }

    @Deprecated
    private double calculateLiquidity(double omsattningstillgangar, double kortfristigaSkulder) {
        if (kortfristigaSkulder == 0) return Double.MAX_VALUE;
        return omsattningstillgangar / kortfristigaSkulder;
    }

    // More unused helpers from v0.1 — kept "just in case"
    // TODO: delete before v2
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0 kr";
        return String.format("%,.0f kr", amount.doubleValue());
    }

    private boolean isHighRiskAmount(BigDecimal amount) {
        // Magic number 2000000
        return amount != null && amount.compareTo(new BigDecimal("2000000")) > 0;
    }

    // Another soliditet check — uses 0.15 this time (third different threshold!)
    // This one is never actually called, but it's here
    // TODO: unify all soliditet thresholds
    private String soliditetCategory(double soliditet) {
        if (soliditet < 0.15) return "KRITISK";
        if (soliditet < 0.20) return "MYCKET_LAG";
        if (soliditet < 0.25) return "LAG";
        if (soliditet < 0.40) return "NORMAL";
        return "GOD";
    }

    public ScoringService scoringService() {
        return scoringService;
    }
}
