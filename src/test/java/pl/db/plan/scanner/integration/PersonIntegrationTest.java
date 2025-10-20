package pl.db.plan.scanner.integration;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.db.plan.scanner.configuration.JpaConfiguration;
import pl.db.plan.scanner.entities.ActivityLog;
import pl.db.plan.scanner.entities.Address;
import pl.db.plan.scanner.entities.Person;
import pl.db.plan.scanner.generators.CityGenerator;
import pl.db.plan.scanner.generators.EntityGenerator;
import pl.db.plan.scanner.repositories.ActivityLogRepository;
import pl.db.plan.scanner.repositories.AddressRepository;
import pl.db.plan.scanner.repositories.PersonRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.properties.hibernate.use_sql_comments=true"
})
@Import(JpaConfiguration.class)
@Testcontainers
class PersonIntegrationTest {

    @Autowired
    @SuppressWarnings("unused")
    private PersonRepository personRepository;

    @Autowired
    @SuppressWarnings("unused")
    private AddressRepository addressRepository;

    @Autowired
    @SuppressWarnings("unused")
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private EntityGenerator generator;

    @Container
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("unused")
    @DynamicPropertySource
    protected static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    @Transactional
    void shouldPersistPersonWithRelations() {
        Person person = generator.createPerson();
        Address address = generator.createAddress(person);
        List<ActivityLog> logs = generator.createActivityLogs(person);

        Person savedPerson = personRepository.save(person);
        assertNotNull(savedPerson.getId());

        List<Person> people = personRepository.findAll();
        assertEquals(1, people.size());

        Person saved = people.getFirst();
        assertEquals(person.getName(), saved.getName());
        assertEquals(1, saved.getAddresses().size());
        String city = saved.getAddresses().getFirst().getCity();
        assertEquals(address.getCity(), city);
        assertTrue(CityGenerator.CITIES.contains(city));
        assertEquals(logs.size(), saved.getActivityLogs().size());
    }
}
