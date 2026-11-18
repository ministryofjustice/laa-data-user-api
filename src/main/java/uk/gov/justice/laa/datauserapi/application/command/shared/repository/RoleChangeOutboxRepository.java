package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import uk.gov.justice.laa.datauserapi.entity.RoleChangeOutboxEvent;

public interface RoleChangeOutboxRepository extends JpaRepository<RoleChangeOutboxEvent, Long> {
    @Query(value = """
            select e.* from role_change_outbox e
            where e.delivered_at is null and e.next_attempt_at <= :now
              and (e.lease_until is null or e.lease_until <= :now)
              and not exists (
                select 1 from role_change_outbox older
                where older.group_id = e.group_id and older.id < e.id and older.delivered_at is null)
            order by e.id
            limit 20 for update skip locked
            """, nativeQuery = true)
    List<RoleChangeOutboxEvent> findClaimable(Instant now);

    boolean existsByProfileIdAndDeliveredAtIsNull(java.util.UUID profileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from RoleChangeOutboxEvent event where event.id = :id")
    java.util.Optional<RoleChangeOutboxEvent> findForUpdate(Long id);
}
