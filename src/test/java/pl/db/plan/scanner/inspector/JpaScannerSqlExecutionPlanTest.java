package pl.db.plan.scanner.inspector;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.db.plan.scanner.configuration.JpaConfiguration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.properties.hibernate.use_sql_comments=true"
})
@Import(JpaConfiguration.class)
@Testcontainers
public class JpaScannerSqlExecutionPlanTest extends AbstractSqlExecutionPlanTest {

    private static final Pattern PARAM_NAME_PATTERN = Pattern.compile(":(\\w+)");
    private static final Integer NUMBER_OF_ENTITIES = 3;
    private static final Integer NUMBER_OF_QUERIES = 7;


    @Autowired
    private ApplicationContext context;

    @Autowired
    private SqlCaptureInspector inspector;

    @Autowired
    private EntityManager entityManager;

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
        inspector.clear();
        var repositories = findRepositories();
        var jpaQueries = findQueries(repositories);
        var nativeQueries = translateToNativeSql(jpaQueries);
        //fill with data, recalculate, explain plan
        assertNotNull(nativeQueries);
    }

    private Set<String> findRepositories() {
        Map<String, Object> repos = context.getBeansWithAnnotation(Repository.class);
        var repositories = repos.values().stream().map(o -> {
            Class<?>[] interfaces = o.getClass().getInterfaces();
            for (Class<?> jpaRepository : interfaces) {
                if (JpaRepository.class.isAssignableFrom(jpaRepository)) {
                    return jpaRepository.getName();
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toSet());
        assertNotNull(repositories);
        assertEquals(NUMBER_OF_ENTITIES, repositories.size());
        return repositories;
    }

    private Map<Class<?>, List<Method>> findQueries(Set<String> classes) {
        Map<Class<?>, List<Method>> queries = new HashMap<>();
        classes.forEach(c -> {
            try {
                Class<?> repoClass = Class.forName(c);
                ParameterizedType genericInterface = (ParameterizedType) repoClass.getGenericInterfaces()[0];
                Type entity = genericInterface.getActualTypeArguments()[0];
                String entityName = entity.getTypeName();
                Class<?> entityClass = Class.forName(entityName);

                for (Method method : repoClass.getDeclaredMethods()) {
                    Query queryAnnotation = method.getAnnotation(Query.class);
                    if (queryAnnotation != null) {
                        var elements = queries.getOrDefault(entityClass, new ArrayList<>());
                        elements.add(method);
                        queries.put(entityClass, elements);
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        assertNotNull(queries);
        assertEquals(NUMBER_OF_ENTITIES, queries.size(), "We have 3 database entities");
        assertEquals(NUMBER_OF_QUERIES, queries.values().stream().mapToInt(List::size).sum(), "We have 7 jpa queries");
        return queries;
    }

    private List<String> translateToNativeSql(Map<Class<?>, List<Method>> jpaQueries) {
        jpaQueries.forEach((clazz, methods) -> methods.forEach(m -> runQuery(m, clazz)));
        List<String> capturedSql = inspector.getNativeSql();
        capturedSql.forEach(System.out::println);
        assertEquals(NUMBER_OF_QUERIES, capturedSql.size(), "We executed all native queries");
        return capturedSql;
    }

    private <T> void runQuery(Method method, Class<T> clazz) {
        // retrieve jpql query from method
        Query queryAnnotation = method.getAnnotation(Query.class);
        String jpql = queryAnnotation.value();

        // depends on sql command, slightly different approach
        if (jpql.toLowerCase().startsWith("select")) {
            TypedQuery<T> query = entityManager.createQuery(jpql, clazz);
            fillQueryParameters(method, jpql, query);
            query.getResultList();
        } else {
            var query = entityManager.createQuery(jpql);
            fillQueryParameters(method, jpql, query);
            query.executeUpdate();
        }
    }

    private void fillQueryParameters(Method method, String jpql, jakarta.persistence.Query query) {
        Set<String> paramNames = extractNamedParameters(jpql);
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Class<?>[] paramTypes = method.getParameterTypes();

        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation annotation : paramAnnotations[i]) {
                if (annotation instanceof Param) {
                    String name = ((Param) annotation).value();
                    if (paramNames.contains(name)) {
                        Object value = Instancio.of(paramTypes[i]).create();
                        values.put(name, value);
                    }
                }
            }
        }
        values.forEach(query::setParameter);
    }

    private Set<String> extractNamedParameters(String jpql) {
        Matcher matcher = PARAM_NAME_PATTERN.matcher(jpql);
        Set<String> params = new HashSet<>();
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }
}
