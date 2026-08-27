package se.comerit.resurs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import se.comerit.resurs.service.AuthService;


@Controller
public class AuthController {

    private final AuthService service;

    AuthController(AuthService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String root() { return "redirect:/login"; }

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        if (session.getAttribute("userId") != null) {
            String role = (String) session.getAttribute("role");
            if ("caseWorker".equals(role)) {
                return "redirect:/backoffice";
            }
            return "redirect:/apply";
        }
        model.addAttribute("error", null);
        return "login";
    }

    // BankID mock — hardcoded org numbers, real BankID integration skipped
    // TODO: replace with real BankID integration
    @PostMapping("/login/company")
    public String loginCompany(@RequestParam("orgNumber") String orgNumber,
                               HttpSession session,
                               Model model) {
        // TODO: replace with real BankID integration
        if (orgNumber.equals("556000-1234") || orgNumber.equals("556000-5678")) {
            // BankID authentication successful (mock)
            return service.findCompany(orgNumber).map(company -> {
                session.setAttribute("userId", company.getId());
                session.setAttribute("role", "company");
                session.setAttribute("orgNumber", orgNumber);
                session.setAttribute("companyName", company.getName());
                session.setAttribute("companyId", company.getId());
                return "redirect:/apply";
            }).orElseGet(() -> {
                model.addAttribute("error", "Företaget hittades inte i systemet.");
                model.addAttribute("activeTab", "company");
                return "login";
            });
        } else {
            // Not in whitelist — BankID mock rejects
            model.addAttribute("error", "BankID-autentisering misslyckades. Org.nummer ej godkänt.");
            model.addAttribute("activeTab", "company");
            return "login";
        }
    }

    // Case worker login with MD5 password — SQL built with string concat (injection surface)
    // TODO: parameterize this query and use bcrypt
    @PostMapping("/login/caseWorker")
    public String loginCaseWorker(@RequestParam("email") String email,
                                  @RequestParam("password") String password,
                                  HttpSession session,
                                  Model model) {
        return service.loginCaseWorker(email, password).map(worker -> {
            session.setAttribute("userId", worker.getId());
            session.setAttribute("role", "caseWorker");
            session.setAttribute("workerName", worker.getName());
            session.setAttribute("workerEmail", worker.getEmail());
            return "redirect:/backoffice";
        }).orElseGet(() -> {
            model.addAttribute("error", "Felaktigt användarnamn eller lösenord.");
            model.addAttribute("activeTab", "caseWorker");
            return "login";
        });
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
