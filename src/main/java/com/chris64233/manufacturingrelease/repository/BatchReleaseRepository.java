package com.chris64233.manufacturingrelease.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.manufacturingrelease.domain.BatchRelease;

public interface BatchReleaseRepository extends JpaRepository<BatchRelease, Long> {

    Optional<BatchRelease> findByReleaseNo(String releaseNo);

    @Query("select r from BatchRelease r left join fetch r.evidences where r.releaseNo = :releaseNo")
    Optional<BatchRelease> findWithEvidencesByReleaseNo(@Param("releaseNo") String releaseNo);

    @Query("select r from BatchRelease r where r.batch.id = :batchId")
    Optional<BatchRelease> findByBatchId(@Param("batchId") Long batchId);
}
