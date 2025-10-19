package pl.db.plan.scanner.generators;

import org.instancio.Random;
import org.instancio.generator.Generator;

import java.util.List;

public class ActionGenerator implements Generator<String> {
    private final List<String> actions = List.of("LOGIN", "LOGOUT", "UPDATE", "DELETE");

    @Override
    public String generate(Random random) {
        return actions.get(random.intRange(0, actions.size() - 1));
    }
}
