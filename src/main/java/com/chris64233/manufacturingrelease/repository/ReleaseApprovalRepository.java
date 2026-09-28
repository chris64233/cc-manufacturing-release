package com.chris64233.manufacturingrelease.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.manufacturingrelease.domain.ReleaseApproval;

public interface ReleaseApprovalRepository extends JpaRepository<ReleaseApproval, Long> {

    @Query("select a from ReleaseApproval a where a.batch.id = :batchId order by a.grantedAt, a.id")
    List<ReleaseApproval> findByBatchId(@Param("batchId") Long batchId);

    @Query("select a from ReleaseApproval a where a.batch.id = :batchId and a.active = true order by a.id")
    List<ReleaseApproval> findActiveByBatchId(@Param("batchId") Long batchId);

    /** 放行事务中锁定有效审批行，防止审批在放行校验后被并发撤回。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ReleaseApproval a where a.batch.id = :batchId and a.active = true order by a.id")
    List<ReleaseApproval> lockActiveByBatchId(@Param("batchId") Long batchId);
}
