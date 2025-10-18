package pl.db.plan.scanner.integration;

import jakarta.transaction.Transactional;
import org.instancio.Instancio;
import org.instancio.Model;
import org.instancio.Random;
import org.instancio.generator.Generator;
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
import pl.db.plan.scanner.repositories.ActivityLogRepository;
import pl.db.plan.scanner.repositories.AddressRepository;
import pl.db.plan.scanner.repositories.PersonRepository;

import java.util.List;

import static org.instancio.Select.field;
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
        Person person = createPerson();
        Address address = createAddress(person);
        List<ActivityLog> logs = createActivityLogs(person);

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

    private static List<ActivityLog> createActivityLogs(Person person) {
        Model<ActivityLog> model = Instancio.of(ActivityLog.class)
                .ignore(field(ActivityLog::getId))
                .ignore(field(ActivityLog::getPerson))
                .generate(field(ActivityLog::getAction), gen -> new ActionGenerator())
                .generate(field(ActivityLog::getTimestamp), gen -> gen.temporal().localDateTime())
                .toModel();

        ActivityLog log = Instancio.create(model);
        log.setPerson(person);

        ActivityLog log2 = Instancio.create(model);
        log2.setPerson(person);

        List<ActivityLog> logs = List.of(log, log2);
        person.setActivityLogs(logs);
        return logs;
    }

    private static Address createAddress(Person person) {
        Model<Address> model = Instancio.of(Address.class)
                .ignore(field(Address::getId))
                .ignore(field(Address::getPerson))
                .generate(field(Address::getCity), gen -> new CityGenerator())
                .generate(field(Address::getStreet), gen -> gen.text().word())
                .generate(field(Address::getPostalCode), gen -> gen.text().word())
                .toModel();

        Address address = Instancio.create(model);
        address.setPerson(person);
        person.setAddresses(List.of(address));
        return address;
    }

    private Person createPerson() {
        Model<Person> model = Instancio.of(Person.class)
                .ignore(field(Person::getId))
                .ignore(field(Person::getActivityLogs))
                .ignore(field(Person::getAddresses))
                .generate(field(Person::getName), gen -> new NameGenerator())
                .generate(field(Person::getEmail), gen -> gen.text().word())
                .toModel();
        return Instancio.create(model);
    }

    private static class CityGenerator implements Generator<String> {
        private static final List<String> CITIES = List.of("London", "Berlin", "Paris", "Roma", "Warsaw");

        @Override
        public String generate(Random random) {
            return CITIES.get(random.intRange(0, CITIES.size() - 1));
        }
    }

    private static class ActionGenerator implements Generator<String> {
        private final List<String> actions = List.of("LOGIN", "LOGOUT", "UPDATE", "DELETE");

        @Override
        public String generate(Random random) {
            return actions.get(random.intRange(0, actions.size() - 1));
        }
    }

    private static class NameGenerator implements Generator<String> {
        private final List<String> names = List.of("John", "Kate", "Michael", "Sara");

        @Override
        public String generate(Random random) {
            return names.get(random.intRange(0, names.size() - 1));
        }
    }
}
