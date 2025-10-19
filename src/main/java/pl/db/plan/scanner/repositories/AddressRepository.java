package pl.db.plan.scanner.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.db.plan.scanner.entities.Address;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    @Query("SELECT a FROM Address a WHERE a.person.id = :personId")
    List<Address> findByPersonId(@Param("personId") Long personId);

    @Query("SELECT a FROM Address a WHERE LOWER(a.city) = LOWER(:city)")
    List<Address> findByCityIgnoreCase(@Param("city") String city);

}
