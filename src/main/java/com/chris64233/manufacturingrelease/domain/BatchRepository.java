package com.chris64233.manufacturingrelease.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    Optional<Batch> findByBatchNo(String batchNo);

    /**
     * 按批次号取得批次并加行级悲观写锁。所有修改批次状态/资料/放行的事务都先执行此查询，
     * 使"补录结果、撤回审批、放行"等针对同一批次的并发动作严格串行化，
     * 从而杜绝依据不完整的已放行批次。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Batch b where b.batchNo = :batchNo")
    Optional<Batch> findByBatchNoForUpdate(@Param("batchNo") String batchNo);
}
