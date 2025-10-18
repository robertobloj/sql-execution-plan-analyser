package pl.db.plan.scanner.inspector;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import pl.db.plan.scanner.repositories.ActivityLogRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
public class SqlExecutionPlanTest {

    private static final Integer NUMBER_OF_ACTIVITY_LOGS = 10_000;
    private static final BigDecimal MAX_COST = BigDecimal.valueOf(1000);

    private static final Pattern PATTERN = Pattern.compile("cost=\\d+\\.\\d+..(\\d+\\.\\d+)");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ActivityLogRepository activityLogRepository;

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

    @Transactional
    private void insertBulkActivityLogs(Integer max) {
        List<ActivityLog> logs = IntStream.range(0, max)
                .mapToObj(i -> {
                    ActivityLog log = new ActivityLog();
                    log.setAction(i % 2 == 0 ? "LOGIN" : "LOGOUT");
                    log.setTimestamp(LocalDateTime.now().minusDays(i % 300));
                    return log;
                })
                .toList();

        List<ActivityLog> result = activityLogRepository.saveAll(logs);
    }

    private void recalculateStatistics() throws SQLException {
        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement()) {
            stmt.execute("ANALYZE");
        }
    }

    private QueryDetails explainPlan(String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement("EXPLAIN " + sql);
            ResultSet rs = stmt.executeQuery()) {
                StringBuilder plan = new StringBuilder();
                while (rs.next()) {
                    plan.append(rs.getString(1)).append("\n");
                }
                String planText = plan.toString();
                boolean hasSeqScan = planText.contains("Seq Scan");
                BigDecimal totalCost = extractTotalCost(planText);
                return new QueryDetails(sql, hasSeqScan, totalCost);
            }
    }

    private BigDecimal extractTotalCost(String planText) {
        Matcher matcher = PATTERN.matcher(planText);
        if (matcher.find()) {
            String group = matcher.group(1);
            return new BigDecimal(group);
        }
        fail("Could not extract cost from plan");
        return BigDecimal.ZERO;
    }


}
