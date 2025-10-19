package pl.db.plan.scanner.generators;

import org.instancio.Random;
import org.instancio.generator.Generator;

import java.util.List;

public class CityGenerator implements Generator<String> {
    public static final List<String> CITIES = List.of("London", "Berlin", "Paris", "Roma", "Warsaw");

    @Override
    public String generate(Random random) {
        return CITIES.get(random.intRange(0, CITIES.size() - 1));
    }
}
