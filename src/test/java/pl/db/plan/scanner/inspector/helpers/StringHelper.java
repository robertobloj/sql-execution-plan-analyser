package pl.db.plan.scanner.inspector.helpers;

import pl.db.plan.scanner.inspector.records.ExecutionPlanRecord;

import java.util.ArrayList;
import java.util.List;

public class StringHelper {

    private static final int TOTAL_WIDTH = 120;
    private static final int SQL_WIDTH = 90;
    private static final int FULL_SCAN_WIDTH = 10;
    private static final int COST_WIDTH = 10;

    public void printTable(List<ExecutionPlanRecord> records) {
        String formatHeader = "| %-" + SQL_WIDTH + "s | %" + FULL_SCAN_WIDTH + "s | %" + COST_WIDTH +"s |%n";
        String formatRow    = "| %-" + SQL_WIDTH + "s | %" + FULL_SCAN_WIDTH + "s | %10.2f |%n";
        String formatSecondRow = "| %-" + SQL_WIDTH + "s | %" + FULL_SCAN_WIDTH + "s | %" + COST_WIDTH + "s |%n";

        printHeader(formatHeader);
        records.forEach(r -> {
            List<String> wrapped = wrapText(r.sql());
            for (int i = 0; i < wrapped.size(); i++) {
                if (i == 0) {
                    System.out.format(formatRow, wrapped.get(i), r.fullScan(), r.cost());
                } else {
                    System.out.format(formatSecondRow, wrapped.get(i), "", "");
                }
            }
            System.out.println("-".repeat(TOTAL_WIDTH));
        });
    }

    private List<String> wrapText(String text) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            if (line.length() + word.length() + 1 > SQL_WIDTH) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            if (!line.isEmpty()) {
                line.append(" ");
            }
            line.append(word);
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private void printHeader(String formatHeader) {
        System.out.println("\n\n");
        System.out.println("-".repeat(TOTAL_WIDTH));
        System.out.format(formatHeader, "SQL", "Full Scan", "Cost");
        System.out.println("=".repeat(TOTAL_WIDTH));
    }
}
