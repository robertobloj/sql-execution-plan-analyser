package pl.db.plan.scanner.repositories;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.db.plan.scanner.entities.Person;

import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
    @Query("SELECT p FROM Person p WHERE p.name = :name")
    List<Person> findByName(@Param("name") String name);
}
