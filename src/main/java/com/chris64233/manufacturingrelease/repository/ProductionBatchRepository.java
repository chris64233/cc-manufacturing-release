package com.chris64233.manufacturingrelease.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.manufacturingrelease.domain.ProductionBatch;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {

    Optional<ProductionBatch> findByBatchNo(String batchNo);

    boolean existsByBatchNo(String batchNo);

    /**
     * 对批次行加写锁。批次上的所有变更操作（补录结果、偏差处置、审批/撤回、放行、撤销）
     * 都先获取该锁，从而与放行事务严格串行，杜绝依据不完整的放行。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ProductionBatch b where b.batchNo = :batchNo")
    Optional<ProductionBatch> lockByBatchNo(@Param("batchNo") String batchNo);
}
