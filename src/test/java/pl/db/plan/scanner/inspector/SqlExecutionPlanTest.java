package pl.db.plan.scanner.inspector;

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
import pl.db.plan.scanner.repositories.ActivityLogRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.properties.hibernate.use_sql_comments=true"
})
@Import(JpaConfiguration.class)
@Testcontainers
public class SqlExecutionPlanTest {

    private static final Integer NUMBER_OF_ACTIVITY_LOGS = 10_000;

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
    void shouldInsertAndAnalyzeQueryPlan() throws Exception {
        shouldInsertBulkActivityLogs();
        recalculateStatistics();
        printExplainPlan("SELECT * FROM activity_log WHERE action = 'LOGIN'");
    }

    @Transactional
    void shouldInsertBulkActivityLogs() {
        List<ActivityLog> logs = IntStream.range(0, NUMBER_OF_ACTIVITY_LOGS)
                .mapToObj(i -> {
                    ActivityLog log = new ActivityLog();
                    log.setAction(i % 2 == 0 ? "LOGIN" : "LOGOUT");
                    log.setTimestamp(LocalDateTime.now().minusDays(i % 300));
                    return log;
                })
                .toList();

        List<ActivityLog> result = activityLogRepository.saveAll(logs);
        assertEquals(NUMBER_OF_ACTIVITY_LOGS, result.size());
    }

    private void recalculateStatistics() throws SQLException {
        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement()) {
            stmt.execute("ANALYZE");
        }
    }

    private void printExplainPlan(String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement("EXPLAIN " + sql);
            ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String executionPlan = rs.getString(1);
                System.out.println(executionPlan);
            }
        }
    }
}
