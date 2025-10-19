package pl.db.plan.scanner.inspector;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.db.plan.scanner.configuration.JpaConfiguration;

import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.properties.hibernate.use_sql_comments=true"
})
@Import(JpaConfiguration.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SqlExecutionPlanTest extends AbstractSqlExecutionPlanTest {

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
    @Order(1)
    void testGoodQueryPlanByIndex() {
        insertBulkActivityLogs(NUMBER_OF_ACTIVITY_LOGS);
        try {
            recalculateStatistics();

            var goodQueryPlan = explainPlan("SELECT * FROM activity_log WHERE action = 'LOGIN'");
            assertThat(goodQueryPlan.fullScan()).as("Query does not have full table scan").isFalse();
            assertThat(goodQueryPlan.cost()).as("Expected cost < " + MAX_COST).isLessThanOrEqualTo(MAX_COST);
        } catch (SQLException e) {
            fail("good query test fail due to sql exception", e);
        }
    }

    @Test
    @Order(2)
    void testBadQueryPlanWithoutIndex() {
        insertBulkActivityLogs(NUMBER_OF_ACTIVITY_LOGS * 10);
        try {
            recalculateStatistics();

            var badQueryPlan = explainPlan("SELECT * FROM activity_log WHERE timestamp = '" + LocalDateTime.now().minusDays(30) +"'");
            assertThat(badQueryPlan.fullScan()).as("Expected full table scan").isTrue();
            assertThat(badQueryPlan.cost()).as("Expected cost > " + MAX_COST).isGreaterThan(MAX_COST);
        } catch (SQLException e) {
            fail("bad query test fail due to sql exception", e);
        }
    }

}
