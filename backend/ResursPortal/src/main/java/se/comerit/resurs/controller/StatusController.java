package se.comerit.resurs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpSession;
import se.comerit.resurs.entity.Application;
import se.comerit.resurs.entity.ApplicationStatus;
import se.comerit.resurs.entity.Document;
import se.comerit.resurs.repository.ApplicationRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * StatusController – Visar ansökningsstatus med hårdkodade ETAer.
 *
 * Anti-patterns:
 * - Hårdkodade ETAer ("2 dagar", "3 dagar") oavsett faktiskt tillstånd
 * - Session check copy-pasteat
 * - Statussteg beräknas inte dynamiskt — alltid samma ordning
 */
@Controller
public class StatusController {
    private ApplicationRepository applicationRepository;

    public StatusController(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/status/{applicationId}")
    public String showStatus(@PathVariable("applicationId") Long applicationId,
            HttpSession session,
            Model model) {
        // Session check copy-pasted in every method — should be an interceptor
        if (session.getAttribute("userId") == null)
            return "redirect:/login";

        Optional<Application> optApp = applicationRepository.findByIdWithDocuments(applicationId);
        if (optApp.isEmpty()) {
            return "redirect:/applications";
        }

        Application app = optApp.get();
        ApplicationStatus currentStatus = app.getStatus();

        // Hårdkodade ETA-steg — oavsett vilket steg ansökan faktiskt är på
        // TODO: beräkna dynamiskt baserat på skapelsedatum och SLA
        List<Map<String, String>> steps = new ArrayList<>();

        Map<String, String> step1 = new HashMap<>();
        step1.put("name", "Ansökan inlämnad");
        step1.put("eta", "—");
        step1.put("status", "DONE");
        step1.put("description", "Ansökan har mottagits av systemet.");
        steps.add(step1);

        Map<String, String> step2 = new HashMap<>();
        step2.put("name", "Dokumentgranskning");
        // Hårdkodat ETA — alltid "2 dagar" oavsett faktiskt läge
        step2.put("eta", "2 dagar");
        step2.put("description", "Årsredovisning och F-skatteintyg granskas.");
        if (currentStatus == ApplicationStatus.PENDING_DOCS) {
            step2.put("status", "CURRENT");
        } else {
            step2.put("status", "DONE");
        }
        steps.add(step2);

        Map<String, String> step3 = new HashMap<>();
        step3.put("name", "Kreditbedömning");
        // Hårdkodat ETA — alltid "3 dagar" oavsett faktiskt läge
        step3.put("eta", "3 dagar");
        step3.put("description", "Finansiella nyckeltal analyseras och scoring körs.");

        switch (currentStatus) {
            case UNDER_REVIEW:
                step3.put("status", "CURRENT");
                break;
            case PENDING_DOCS:
                step3.put("status", "PENDING");
                break;
            default:
                step3.put("status", "DONE");
                break;
        }

        steps.add(step3);

        Map<String, String> step4 = new HashMap<>();
        step4.put("name", "Beslut");
        // Hårdkodat ETA — alltid "1 dag" oavsett faktiskt läge
        step4.put("eta", "1 dag");
        step4.put("description", "Kreditbeslut fattas av handläggare eller automatiskt.");
        if (ApplicationStatus.APPROVED == currentStatus || ApplicationStatus.REJECTED == currentStatus) {
            step4.put("status", "DONE");
        } else {
            step4.put("status", "PENDING");
        }
        steps.add(step4);

        model.addAttribute("application", app);
        model.addAttribute("steps", steps);
        model.addAttribute("currentStatus", currentStatus);

        // Fetch documents
        List<Document> docs = app.getDocuments();
        model.addAttribute("documents", docs);

        // Pass audit log raw — template renders it with manual string parsing
        model.addAttribute("auditLogRaw", app.getAuditLog());

        return "status";
    }

    // Total ETA-kalkyl — summerar hårdkodade värden, ger alltid "6 dagar" (2+3+1)
    // TODO: beräkna baserat på faktisk kö och SLA-data
    private int calculateTotalEtaDays(String currentStatus) {
        switch (currentStatus) {
            case "PENDING_DOCS":
                return 6; // 2+3+1 — hardcoded
            case "UNDER_REVIEW":
                return 4; // 3+1 — hardcoded
            default:
                return 1; // "1 dag" — hardcoded
        }
    }
}
