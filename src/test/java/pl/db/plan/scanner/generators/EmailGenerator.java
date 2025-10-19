package pl.db.plan.scanner.generators;

import org.instancio.Random;
import org.instancio.generator.Generator;

import java.util.random.RandomGenerator;

public class EmailGenerator implements Generator<String> {

    private static final String[] DOMAINS = {
        "example.com", "test.org", "mail.net", "demo.io", "sample.dev"
    };

    private final RandomGenerator randomGenerator = RandomGenerator.getDefault();

    @Override
    public String generate(Random random) {
        String username = generateUsername(random);
        String domain = DOMAINS[randomGenerator.nextInt(0, DOMAINS.length)];
        return username + "@" + domain;
    }

    private String generateUsername(Random random) {
        int length = randomGenerator.nextInt(5, 12);; // 5–12
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = (char) ('a' + randomGenerator.nextInt(1, 26));
            sb.append(c);
        }
        return sb.toString();
    }
}
