package pl.db.plan.scanner.generators;

import org.instancio.Random;
import org.instancio.generator.Generator;

import java.util.List;

public class NameGenerator implements Generator<String> {
    private final List<String> names = List.of("John", "Kate", "Michael", "Sara");

    @Override
    public String generate(Random random) {
        return names.get(random.intRange(0, names.size() - 1));
    }
}
