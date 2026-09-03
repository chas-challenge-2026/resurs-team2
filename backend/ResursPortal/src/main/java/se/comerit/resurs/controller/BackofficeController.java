package se.comerit.resurs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Decision;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.repository.ApplicationRepository;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * BackofficeController – Handläggargränssnitt för manuell granskning.
 *
 * Anti-patterns:
 * - Audit log uppdateras via JSON string manipulation
 * - Ingen e-postnotifiering vid beslut
 * - Session check copy-pasteat
 * - Ingen pagination — hämtar ALLA ansökningar i REVIEW
 */
@Controller("legacyBackofficeController")
@Profile("!v2")
public class BackofficeController {

    private final ApplicationRepository applicationRepository;

    BackofficeController(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/backoffice")
    public String backofficeOverview(HttpSession session, Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null)
            return "redirect:/login";
        if (!"caseWorker".equals(session.getAttribute("role")))
            return "redirect:/login";

        // Hämtar ALLA UNDER_REVIEW — ingen pagination, ingen sortering, inget index
        // TODO: lägg till pagination och index på status-kolumnen

        List<Application> reviewApplications = applicationRepository
                .findByStatusOrderByCreatedAtAsc(ApplicationStatus.UNDER_REVIEW);
        // Also get approved/rejected for history — same query pattern, no reuse
        List<Application> devidedApplications = applicationRepository.findByStatusInOrderByUpdatedAtDesc(
                List.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED),
                PageRequest.of(0, 20)).toList();

        model.addAttribute("reviewApplications", reviewApplications);
        model.addAttribute("decidedApplications", devidedApplications);
        model.addAttribute("workerName", session.getAttribute("workerName"));
        model.addAttribute("reviewCount", reviewApplications.size());
        return "backoffice";
    }

    @PostMapping("/backoffice/decide")
    public String decide(@RequestParam("applicationId") Long applicationId,
            @RequestParam("decision") String decision,
            @RequestParam(value = "comment", defaultValue = "") String comment,
            HttpSession session,
            Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null)
            return "redirect:/login";
        if (!"caseWorker".equals(session.getAttribute("role")))
            return "redirect:/login";

        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            return "redirect:/backoffice";
        }

        String workerName = (String) session.getAttribute("workerName");
        ApplicationStatus newStatus = "APPROVED".equals(decision) ? ApplicationStatus.APPROVED
                : ApplicationStatus.REJECTED;

        applicationRepository.findById(applicationId).ifPresent(application -> {
            application.setStatus(newStatus);
            application.setDecision(Decision.valueOf(decision));
            applicationRepository.save(application);
        });

        // Append to audit log JSON blob — same string manipulation as elsewhere
        // No email notification sent — TODO: skicka e-post till företaget
        String auditEntry = "{\"ts\":\"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "\",\"action\":\"MANUAL_DECISION\",\"decision\":\"" + decision
                + "\",\"worker\":\"" + workerName.replace("\"", "'") + "\""
                + (comment.isEmpty() ? "" : ",\"comment\":\"" + comment.replace("\"", "'") + "\"")
                + "}";

        Application currentApplication = applicationRepository.findById(applicationId).orElseThrow();
        String currentLog = currentApplication.getAuditLog();

        String updatedLog;
        if (currentLog == null || currentLog.equals("[]")) {
            updatedLog = "[" + auditEntry + "]";
        } else {
            updatedLog = currentLog.substring(0, currentLog.lastIndexOf("]")) + "," + auditEntry + "]";
        }

        currentApplication.setAuditLog(updatedLog);
        applicationRepository.save(currentApplication);

        // No email notification — TODO: implement email via Spring Mail in v2
        // TODO: notify company via email when decision is made

        return "redirect:/backoffice";
    }

    @GetMapping("/backoffice/application/{id}")
    public String viewApplicationDetail(
            @RequestParam(value = "id", required = false) Long pathId,
            @org.springframework.web.bind.annotation.PathVariable("id") Long id,
            HttpSession session,
            Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null)
            return "redirect:/login";
        if (!"caseWorker".equals(session.getAttribute("role")))
            return "redirect:/login";

        return applicationRepository.findByIdWithDocuments(id).map(application -> {
            model.addAttribute("application", application);
            model.addAttribute("auditLogRaw", application.getAuditLog());
            model.addAttribute("workerName", session.getAttribute("workerName"));

            List<Document> documents = application.getDocuments();
            model.addAttribute("documents", documents);

            return "backoffice_detail";
        }).orElseGet(() -> "redirect:/backoffice");
    }
}
