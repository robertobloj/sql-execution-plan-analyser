package pl.db.plan.scanner.inspector;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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
import pl.db.plan.scanner.inspector.records.NativeQueryRecord;
import pl.db.plan.scanner.inspector.regex.SqlRegexHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    private SqlRegexHelper sqlRegexHelper;

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
        var repositories = findRepositories();
        var jpaQueries = findQueries(repositories);
        var nativeQueries = translateToNativeSql(jpaQueries);
        insertBulkPersons(1, 5, 100);
        assertDoesNotThrow(this::recalculateStatistics);
        assertDoesNotThrow(() -> {
            var plans = nativeQueries.stream().map(q -> {
                System.out.println(q);
                var sql = sqlRegexHelper.replacePlaceholders(q);
                System.out.println("FIXED SQL: " + sql);
                try {
                    return explainPlan(sql);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            plans.forEach(p -> {
                assertFalse(p.fullScan());
                assertThat(p.cost()).as("Expected cost < " + MAX_COST).isLessThanOrEqualTo(MAX_COST);
            });
        });

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

    private List<NativeQueryRecord> translateToNativeSql(Map<Class<?>, List<Method>> jpaQueries) {
        List<List<NativeQueryRecord>> capturedSql = jpaQueries.entrySet().stream()
            .map(e -> {
                var clazz = e.getKey();
                var methods = e.getValue();
                return methods.stream().map(m -> runQuery(m, clazz)).toList();
            }).toList();

        List<NativeQueryRecord> flatList = capturedSql.stream().flatMap(List::stream).toList();
        assertEquals(NUMBER_OF_QUERIES, flatList.size(), "We found all queries");
        return flatList;
    }

    private <T> NativeQueryRecord runQuery(Method method, Class<T> clazz) {
        inspector.clear();

        // retrieve jpql query from method
        Query queryAnnotation = method.getAnnotation(Query.class);
        String jpql = queryAnnotation.value();

        Map<String, Object> parameterValues = null;
        // depends on sql command, slightly different approach
        if (jpql.toLowerCase().startsWith("select")) {
            TypedQuery<T> query = entityManager.createQuery(jpql, clazz);
            parameterValues = fillQueryParameters(method, jpql, query);
            query.getResultList();
        } else {
            var query = entityManager.createQuery(jpql);
            parameterValues = fillQueryParameters(method, jpql, query);
            query.executeUpdate();
        }

        List<String> capturedSql = inspector.getNativeSql();
        assertEquals(1, capturedSql.size());
        return new NativeQueryRecord(capturedSql.getFirst(), parameterValues);
    }

    private Map<String, Object> fillQueryParameters(Method method, String jpql, jakarta.persistence.Query query) {
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
        return values;
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
