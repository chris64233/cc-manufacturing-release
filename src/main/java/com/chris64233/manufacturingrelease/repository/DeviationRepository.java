package com.chris64233.manufacturingrelease.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.manufacturingrelease.domain.Deviation;

public interface DeviationRepository extends JpaRepository<Deviation, Long> {

    Optional<Deviation> findByDeviationNo(String deviationNo);

    boolean existsByDeviationNo(String deviationNo);

    @Query("select d from Deviation d where d.batch.id = :batchId order by d.id")
    List<Deviation> findByBatchId(@Param("batchId") Long batchId);

    /** 放行事务中锁定偏差行，防止终态被并发改动。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deviation d where d.batch.id = :batchId order by d.id")
    List<Deviation> lockByBatchId(@Param("batchId") Long batchId);
}
