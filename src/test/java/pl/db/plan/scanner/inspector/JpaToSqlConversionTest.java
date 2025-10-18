package pl.db.plan.scanner.inspector;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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
import pl.db.plan.scanner.entities.Person;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.properties.hibernate.use_sql_comments=true"
})
@Import(JpaConfiguration.class)
@Testcontainers
class JpaToSqlConversionTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SqlCaptureInspector inspector;

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
    void convertJpaQueryToSql() {
        inspector.clear();

        String jpql = "SELECT p FROM Person p WHERE p.name = :name";
        TypedQuery<Person> query = entityManager.createQuery(jpql, Person.class);
        query.setParameter("name", "Robert");
        query.getResultList();

        List<String> capturedSql = inspector.getNativeSql();
        capturedSql.forEach(System.out::println);
        assertEquals(1, capturedSql.size());
    }
}

