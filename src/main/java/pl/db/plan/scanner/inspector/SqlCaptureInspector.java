package pl.db.plan.scanner.inspector;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlCaptureInspector implements StatementInspector {

    private final Pattern pattern = Pattern.compile("(/\\*.*?\\*/)(\\s+.*)", Pattern.DOTALL);

    private final List<String> jpqlSql = new ArrayList<>();
    private final List<String> nativeSql = new ArrayList<>();

    @Override
    public String inspect(String sql) {
        Matcher matcher = pattern.matcher(sql);

        if (matcher.matches()) {
            // we have jpql comment and sql in one string
            String jpqlComment = matcher.group(1).trim();
            String remainingSql = matcher.group(2).trim();

            jpqlSql.add(jpqlComment);
            nativeSql.add(remainingSql);
        } else {
            // just pure sql
            nativeSql.add(sql);
        }
        return sql;
    }

    public List<String> getJpqlSql() {
        return jpqlSql;
    }

    public List<String> getNativeSql() {
        return nativeSql;
    }

    public void clear() {
        nativeSql.clear();
        jpqlSql.clear();
    }
}
