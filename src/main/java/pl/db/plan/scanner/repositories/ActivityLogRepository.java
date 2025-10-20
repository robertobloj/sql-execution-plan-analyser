package pl.db.plan.scanner.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.db.plan.scanner.entities.ActivityLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    @Query("SELECT a FROM ActivityLog a WHERE a.person.id = :personId AND a.action = :action")
    List<ActivityLog> findByPersonIdAndAction(@Param("personId") Long personId, @Param("action") String action);

    @Query("SELECT a FROM ActivityLog a WHERE a.timestamp >= :from")
    List<ActivityLog> findRecentLogs(@Param("from") LocalDateTime from);

    @Modifying
    @Query("UPDATE ActivityLog a SET a.action = :action WHERE a.id = :id")
    void updateActionById(@Param("id") Long id, @Param("action") String action);

    @Modifying
    @Query("UPDATE ActivityLog a SET a.action = :action WHERE a.person.id = :personId")
    int updateActionForPerson(@Param("personId") Long personId, @Param("action") String action);

}
