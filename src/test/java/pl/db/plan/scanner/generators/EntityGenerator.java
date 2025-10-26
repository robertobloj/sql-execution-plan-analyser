package pl.db.plan.scanner.generators;

import org.instancio.Instancio;
import org.instancio.Model;
import pl.db.plan.scanner.entities.ActivityLog;
import pl.db.plan.scanner.entities.Address;
import pl.db.plan.scanner.entities.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.instancio.Select.field;

public class EntityGenerator {

    private final RandomGenerator randomGenerator = RandomGenerator.getDefault();

    public List<Person> createPersons(int numberOfPersons, int maxAddresses, int maxActivities) {
        return IntStream.range(0, numberOfPersons)
                .mapToObj(i -> {
                    var p = createPerson();

                    var numberOfAddresses = randomGenerator.nextInt(0, maxAddresses);
                    IntStream.range(0, numberOfAddresses).forEach(a -> createAddress(p));

                    var numberOfActivities = randomGenerator.nextInt(0, maxActivities);
                    IntStream.range(0, numberOfActivities).forEach(a -> createActivityLogs(p));

                    return p;
                })
                .collect(Collectors.toList());
    }

    public List<ActivityLog> createActivityLogs(Person person) {
        Model<ActivityLog> model = Instancio.of(ActivityLog.class)
                .ignore(field(ActivityLog::getId))
                .ignore(field(ActivityLog::getPerson))
                .generate(field(ActivityLog::getAction), gen -> new ActionGenerator())
                .generate(field(ActivityLog::getTimestamp), gen -> gen.temporal().localDateTime())
                .toModel();

        ActivityLog log = Instancio.create(model);
        log.setPerson(person);
        var logs = person.getActivityLogs();
        if (logs == null) {
            person.setActivityLogs(new ArrayList<>(List.of(log)));
        }
        else {
            logs.add(log);
        }
        return logs;
    }

    public Address createAddress(Person person) {
        Model<Address> model = Instancio.of(Address.class)
                .ignore(field(Address::getId))
                .ignore(field(Address::getPerson))
                .generate(field(Address::getCity), gen -> new CityGenerator())
                .generate(field(Address::getStreet), gen -> gen.text().word())
                .generate(field(Address::getPostalCode), gen -> gen.text().word())
                .toModel();

        Address address = Instancio.create(model);
        address.setPerson(person);
        List<Address> addresses = person.getAddresses();
        if (addresses == null) {
            person.setAddresses(new ArrayList<>(List.of(address)));
        }
        else {
            addresses.add(address);
        }
        return address;
    }

    public Person createPerson() {
        Model<Person> model = Instancio.of(Person.class)
                .ignore(field(Person::getId))
                .ignore(field(Person::getActivityLogs))
                .ignore(field(Person::getAddresses))
                .generate(field(Person::getName), gen -> new NameGenerator())
                .generate(field(Person::getEmail), gen -> new EmailGenerator())
                .toModel();
        return Instancio.create(model);
    }
}
