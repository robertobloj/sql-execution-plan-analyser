package pl.db.plan.scanner.inspector.regex;

import pl.db.plan.scanner.inspector.records.NativeQueryRecord;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SqlRegexHelper {

    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
        "(?:(\\w+\\.)?(\\w+))\\s*(=|<=|>=|<|>)\\s*\\?"
    );
    private static final Pattern EQUAL_PATTERN = Pattern.compile(
    "(?:(\\w+\\.)?(\\w+))\\s*=\\s*(?:(\\w+)\\()?\\?(?:\\))?"
    );
    private static final Pattern IN_PATTERN = Pattern.compile(
    "(?:(\\w+\\.)?(\\w+))\\s+IN\\s*\\(\\s*\\?\\s*\\)"
    );
    private static final Pattern BETWEEN_PATTERN = Pattern.compile(
    "(?:(\\w+\\.)?(\\w+))\\s+BETWEEN\\s+\\?\\s+AND\\s+\\?"
    );
    private static final Pattern LIKE_PATTERN = Pattern.compile(
    "(?:(\\w+\\.)?(\\w+))\\s+LIKE\\s+\\?"
    );
    private static final Pattern SQL_FUNCTION_PATTERN = Pattern.compile(
    "(\\w+)\\s*\\(\\s*((\\w+\\.)?(\\w+))\\s*\\)\\s*=\\s*(\\w+)\\s*\\(\\s*\\?\\s*\\)"
    );
    private static final Pattern SIMPLE_PARAM_PATTERN = Pattern.compile(
    "(?:(\\w+\\.)?(\\w+))\\s*=\\s*\\?"
    );


    public String replacePlaceholders(NativeQueryRecord query) {
        var sql = query.query();
        var paramMap = query.parameterValues();

        sql = replaceEqual(sql, paramMap);
        sql = replaceComparisons(sql, paramMap);
        sql = replaceIn(sql, paramMap);
        sql = replaceBetween(sql, paramMap);
        sql = replaceLike(sql, paramMap);
        sql = replaceFunctions(sql, paramMap);
        return sql;
    }

    public String replaceComparisons(String sql, Map<String, Object> paramMap) {
        Matcher matcher = COMPARISON_PATTERN.matcher(sql);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String alias = matcher.group(1);
            String column = matcher.group(2);
            String operator = matcher.group(3);

            Object value = paramMap.get(column);
            if (value == null) {
                value = paramMap.get(toCamelCase(column));
                if (value == null) {
                    throw new IllegalArgumentException("Value not found for parameter: " + column);
                }
            }

            String formatted = formatValue(value);
            String replacement = (alias != null ? alias : "") + column + " " + operator + " " + formatted;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceFunctions(String sql, Map<String, Object> paramMap) {
        Matcher matcher = SQL_FUNCTION_PATTERN.matcher(sql);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String column = matcher.group(4);
            String rightFunction = matcher.group(5); // np. lower
            Object value = paramMap.get(column);
            if (value == null) {
                value = paramMap.get(toCamelCase(column));
                if (value == null) {
                    throw new IllegalArgumentException("Value not found for parameter: " + column);
                }
            }

            String formatted = formatValue(value);
            String replacement = matcher.group(1) + "(" + matcher.group(2) + ") = " + rightFunction + "(" + formatted + ")";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        sql = result.toString();

        result = new StringBuilder();
        matcher = SIMPLE_PARAM_PATTERN.matcher(sql);

        while (matcher.find()) {
            String column = matcher.group(2); // np. id
            Object value = paramMap.get(column);
            if (value == null) {
                value = paramMap.get(toCamelCase(column));
                if (value == null) {
                    throw new IllegalArgumentException("Value not found for parameter: " + column);
                }
            }
            String formatted = formatValue(value);
            String replacement = (matcher.group(1) != null ? matcher.group(1) : "") + column + " = " + formatted;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceEqual(String sql, Map<String, Object> paramMap) {
        Matcher matcher = EQUAL_PATTERN.matcher(sql);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String alias = matcher.group(1);
            String column = matcher.group(2);
            String function = matcher.group(3);

            Object value = paramMap.get(column);
            if (value == null) {
                value = paramMap.get(toCamelCase(column));
                if (value == null) {
                    throw new IllegalArgumentException("Value not found for parameter: " + column);
                }
            }

            String formatted = formatValue(value);
            String replacement = (function != null ? function + "(" + formatted + ")" : formatted);
            matcher.appendReplacement(result, Matcher.quoteReplacement((alias != null ? alias : "") + column + " = " + replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceIn(String sql, Map<String, Object> paramMap) {
        Matcher matcher = IN_PATTERN.matcher(sql);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String alias = matcher.group(1);
            String column = matcher.group(2);

            Object value = paramMap.get(column);
            if (!(value instanceof Collection<?> values)) {
                throw new IllegalArgumentException("IN requires Collection for: " + column);
            }

            String joined = values.stream()
                    .map(SqlRegexHelper::formatValue)
                    .collect(Collectors.joining(", "));
            matcher.appendReplacement(result, Matcher.quoteReplacement((alias != null ? alias : "") + column + " IN (" + joined + ")"));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceBetween(String sql, Map<String, Object> paramMap) {
        Matcher matcher = BETWEEN_PATTERN.matcher(sql);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String alias = matcher.group(1);
            String column = matcher.group(2);

            Object value = paramMap.get(column);
            if (!(value instanceof List<?> values) || values.size() != 2) {
                throw new IllegalArgumentException("BETWEEN requires List with 2 elements for: " + column);
            }

            String from = formatValue(values.get(0));
            String to = formatValue(values.get(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement((alias != null ? alias : "") + column + " BETWEEN " + from + " AND " + to));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceLike(String sql, Map<String, Object> paramMap) {
        Matcher matcher = LIKE_PATTERN.matcher(sql);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String alias = matcher.group(1);
            String column = matcher.group(2);

            Object value = paramMap.get(column);
            if (!(value instanceof String str)) {
                throw new IllegalArgumentException("LIKE requires String for: " + column);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement((alias != null ? alias : "") + column + " LIKE " + formatValue(str)));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public String toCamelCase(String input) {
        String[] parts = input.split("_");
        if (parts.length == 0) return input;

        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return result.toString();
    }

    private static String formatValue(Object value) {
        return switch(value) {
            case String s -> "'" + s.replace("'", "''") + "'";
            case Timestamp t -> "'" + t.toString() + "'";
            case LocalDateTime d -> "'" + d + "'";
            default -> String.valueOf(value);
        };
    }
}
