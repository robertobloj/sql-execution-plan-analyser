package pl.db.plan.scanner.inspector;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.properties.hibernate.use_sql_comments=true"
})
@Import(JpaConfiguration.class)
@Testcontainers
public class SqlCaptureInspectorTest {

    @Autowired
    private SqlCaptureInspector inspector;

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
    void shouldExtractJpqlCommentAndSql() {
        inspector.clear();
        String input = "/* SELECT p FROM Person p WHERE p.name = :name */ select p1_0.id,p1_0.name from person p1_0 where p1_0.name=?";

        var result = inspector.inspect(input);
        assertEquals(input, result);

        assertEquals(1, inspector.getJpqlSql().size());
        assertEquals(1, inspector.getNativeSql().size());

        String jpqlComment = inspector.getJpqlSql().getFirst();
        String nativeSql = inspector.getNativeSql().getFirst();

        assertEquals("/* SELECT p FROM Person p WHERE p.name = :name */", jpqlComment);
        assertEquals("select p1_0.id,p1_0.name from person p1_0 where p1_0.name=?", nativeSql);
    }

    @Test
    void shouldFailOnMissingComment() {
        inspector.clear();
        String input = "select p1_0.id,p1_0.name from person p1_0 where p1_0.name=?";

        var result = inspector.inspect(input);
        assertEquals(input, result);

        List<String> jpqlComments = inspector.getJpqlSql();
        List<String> nativeSqls = inspector.getNativeSql();
        assertEquals(0, jpqlComments.size());
        assertEquals(1, nativeSqls.size());
    }

    @Test
    void shouldHandleMultilineComment() {
        inspector.clear();
        String input = "/* SELECT p \n FROM Person p \n WHERE p.name = :name */ select p1_0.id,p1_0.name from person p1_0 where p1_0.name=?";
        var result = inspector.inspect(input);
        assertEquals(input, result);

        assertEquals(1, inspector.getJpqlSql().size());
        assertEquals(1, inspector.getNativeSql().size());

        String jpqlComment = inspector.getJpqlSql().getFirst();
        String nativeSql = inspector.getNativeSql().getFirst();

        assertTrue(jpqlComment.contains("SELECT p"), "Comment should contains JPQL query");
        assertTrue(nativeSql.startsWith("select"), "SQL should start from 'select' clause");
    }
}
