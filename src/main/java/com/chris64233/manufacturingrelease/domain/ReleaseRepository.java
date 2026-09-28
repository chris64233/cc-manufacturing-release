package com.chris64233.manufacturingrelease.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRepository extends JpaRepository<Release, Long> {

    Optional<Release> findByReleaseNo(String releaseNo);

    Optional<Release> findByBatchId(Long batchId);

    boolean existsByReleaseNo(String releaseNo);
}
