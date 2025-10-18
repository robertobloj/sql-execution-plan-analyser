package pl.db.plan.scanner.inspector;

import java.math.BigDecimal;

public record QueryDetails(String sql, boolean fullScan, BigDecimal cost) {
}
