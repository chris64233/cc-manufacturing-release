package com.chris64233.manufacturingrelease.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.manufacturingrelease.domain.InspectionResult;

public interface InspectionResultRepository extends JpaRepository<InspectionResult, Long> {

    @Query("select r from InspectionResult r where r.item.id = :itemId order by r.version")
    List<InspectionResult> findByItemIdOrderByVersion(@Param("itemId") Long itemId);

    @Query("select r from InspectionResult r where r.item.id = :itemId and r.current = true")
    Optional<InspectionResult> findCurrentByItemId(@Param("itemId") Long itemId);

    @Query("select coalesce(max(r.version), 0) from InspectionResult r where r.item.id = :itemId")
    int findMaxVersionByItemId(@Param("itemId") Long itemId);

    /** 放行事务中锁定某批次的当前有效结果行，防止被并发替代。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InspectionResult r where r.item.batch.id = :batchId and r.current = true order by r.id")
    List<InspectionResult> lockCurrentByBatchId(@Param("batchId") Long batchId);
}
