package pl.db.plan.scanner.inspector.records;

import java.math.BigDecimal;

public record ExecutionPlanRecord(String sql, boolean fullScan, BigDecimal cost) {
}
