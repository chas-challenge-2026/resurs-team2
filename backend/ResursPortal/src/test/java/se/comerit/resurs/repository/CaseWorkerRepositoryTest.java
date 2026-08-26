package se.comerit.resurs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import se.comerit.resurs.entity.CaseWorker;

@DataJpaTest
@ActiveProfiles("test")
class CaseWorkerRepositoryTest {

    @Autowired
    private CaseWorkerRepository caseWorkerRepository;

    @Test
    void shouldFindCaseWorkerByEmail() {
        Optional<CaseWorker> result = caseWorkerRepository.findByEmail("karin@resurs.se");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Karin Handläggare");
        assertThat(result.get().getPassword()).isEqualTo("482c811da5d5b4bc6d497ffa98491e38");
    }

    @Test
    void shouldReturnEmptyForUnknownEmail() {
        Optional<CaseWorker> result = caseWorkerRepository.findByEmail("unknown@test.se");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllCaseWorkers() {
        List<CaseWorker> workers = caseWorkerRepository.findAll();

        assertThat(workers).hasSize(1);
    }

    @Test
    void shouldPersistNewCaseWorker() {
        CaseWorker worker = new CaseWorker("Test Handläggare", "test@resurs.se", "abc123");

        CaseWorker saved = caseWorkerRepository.save(worker);

        assertThat(saved.getId()).isNotNull();
        assertThat(caseWorkerRepository.findByEmail("test@resurs.se")).isPresent();
    }
}
