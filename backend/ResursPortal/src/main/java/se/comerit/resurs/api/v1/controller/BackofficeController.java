package se.comerit.resurs.api.v1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.comerit.resurs.api.v1.dto.ApplicationDetailsResponse;
import se.comerit.resurs.api.v1.service.BackofficeService;
import se.comerit.resurs.security.UserPrincipal;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController("foo")
@RequestMapping("api/a1/backoffice")
public class BackofficeController {

    private final BackofficeService service;

    public BackofficeController(BackofficeService service) {
        this.service = service;
    }

    @GetMapping
    public String overview() {
        return "";
    }

    @PostMapping("decide")
    public String decide(@RequestBody String entity) {
        //TODO: process POST request
        
        return entity;
    }
    
    @GetMapping("application/{id}")
    public ResponseEntity<ApplicationDetailsResponse> viewApplicationDetails(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        String caseWorker = principal.asCaseWorker().name();
        return service.viewApplicationDetails(id, caseWorker);
    }
    
}
