package pl.db.plan.scanner.inspector;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
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

import static org.junit.jupiter.api.Assertions.fail;

public abstract class AbstractSqlExecutionPlanTest {

    protected static final Integer NUMBER_OF_ACTIVITY_LOGS = 10_000;
    protected static final BigDecimal MAX_COST = BigDecimal.valueOf(1000);
    protected static final Pattern PATTERN = Pattern.compile("cost=\\d+\\.\\d+..(\\d+\\.\\d+)");

    @Autowired
    protected DataSource dataSource;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    protected void recalculateStatistics() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ANALYZE");
        }
    }

    protected QueryDetails explainPlan(String sql) throws SQLException {
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

    protected BigDecimal extractTotalCost(String planText) {
        Matcher matcher = PATTERN.matcher(planText);
        if (matcher.find()) {
            String group = matcher.group(1);
            return new BigDecimal(group);
        }
        fail("Could not extract cost from plan");
        return BigDecimal.ZERO;
    }

    @Transactional
    protected void insertBulkActivityLogs(Integer max) {
        List<ActivityLog> logs = IntStream.range(0, max)
                .mapToObj(i -> {
                    ActivityLog log = new ActivityLog();
                    log.setAction(i % 2 == 0 ? "LOGIN" : "LOGOUT");
                    log.setTimestamp(LocalDateTime.now().minusDays(i % 300));
                    return log;
                })
                .toList();

        activityLogRepository.saveAll(logs);
    }
}
