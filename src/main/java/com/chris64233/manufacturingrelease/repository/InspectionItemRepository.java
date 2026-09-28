package com.chris64233.manufacturingrelease.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.manufacturingrelease.domain.InspectionItem;

public interface InspectionItemRepository extends JpaRepository<InspectionItem, Long> {

    @Query("select i from InspectionItem i left join fetch i.results r "
            + "where i.batch.id = :batchId order by i.id, r.version")
    List<InspectionItem> findWithResultsByBatchId(@Param("batchId") Long batchId);

    @Query("select i from InspectionItem i where i.batch.id = :batchId and i.itemCode = :itemCode")
    Optional<InspectionItem> findByBatchIdAndItemCode(@Param("batchId") Long batchId,
                                                      @Param("itemCode") String itemCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InspectionItem i where i.batch.id = :batchId order by i.id")
    List<InspectionItem> lockByBatchId(@Param("batchId") Long batchId);
}
