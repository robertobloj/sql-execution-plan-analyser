package pl.db.plan.scanner.inspector;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.db.plan.scanner.configuration.JpaConfiguration;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.properties.hibernate.use_sql_comments=true"
})
@Import(JpaConfiguration.class)
@Testcontainers
public class JpaScannerSqlExecutionPlanTest {

    private static final String BASE_PACKAGE = "pl.db.plan.scanner.repositories";

    @Autowired
    private ApplicationContext context;

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    protected static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void shouldFindInvalidExecutionPlan() {
        var repositories = findRepositories();
        var jpaQueries = findQueries(repositories);

        assertNotNull(jpaQueries);
    }

    private Set<String> findRepositories() {
        Map<String, Object> repos = context.getBeansWithAnnotation(Repository.class);
        return repos.values().stream().map(o -> {
            Class<?>[] interfaces = o.getClass().getInterfaces();
            for (Class<?> jpaRepository : interfaces) {
                if (JpaRepository.class.isAssignableFrom(jpaRepository)) {
                    return jpaRepository.getName();
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Set<String> findQueries(Set<String> classes) {
        Set<String> queries = new HashSet<>();
        classes.forEach(c -> {
            try {
                Class<?> repoClass = Class.forName(c);
                for (Method method : repoClass.getDeclaredMethods()) {
                    Query queryAnnotation = method.getAnnotation(Query.class);
                    if (queryAnnotation != null) {
                        String query = queryAnnotation.value();
                        queries.add(query);
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        return queries;
    }
}
