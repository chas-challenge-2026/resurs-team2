package se.comerit.resurs.api.v1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import se.comerit.resurs.security.WithCaseWorker;
import se.comerit.resurs.security.WithCompany;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:company;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("GET /api/v1/companies/me")
    class Me {

        @Test
        void unauthenticatedIs401() throws Exception {
            mockMvc.perform(get("/api/v1/companies/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }

        @Test
        @WithCaseWorker
        void wrongRoleCaseWorkerIs403() throws Exception {
            mockMvc.perform(get("/api/v1/companies/me"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Access Denied"));
        }

        @Test
        @WithCompany(name = "Malmö Fastigheter AB", orgNumber = "556000-1234")
        void returnsCompanyNameAndOrgNumber() throws Exception {
            mockMvc.perform(get("/api/v1/companies/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("Malmö Fastigheter AB"))
                    .andExpect(jsonPath("$.orgNumber").value("556000-1234"));
        }

        @Test
        @WithCompany(name = "Stockholm Teknik AB", orgNumber = "559000-7777")
        void returnsPrincipalDataNotHardcoded() throws Exception {
            mockMvc.perform(get("/api/v1/companies/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("Stockholm Teknik AB"))
                    .andExpect(jsonPath("$.orgNumber").value("559000-7777"));
        }

        @Test
        @WithCompany
        void doesNotLeakInternalFields() throws Exception {
            mockMvc.perform(get("/api/v1/companies/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").doesNotExist())
                    .andExpect(jsonPath("$.role").doesNotExist())
                    .andExpect(jsonPath("$.password").doesNotExist());
        }
    }
}
