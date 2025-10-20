package pl.db.plan.scanner.inspector.records;

import java.util.Map;

public record NativeQueryRecord(String query, Map<String, Object> parameterValues) {
}
