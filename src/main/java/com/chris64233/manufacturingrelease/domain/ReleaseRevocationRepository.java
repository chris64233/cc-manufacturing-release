package com.chris64233.manufacturingrelease.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRevocationRepository extends JpaRepository<ReleaseRevocation, Long> {

    Optional<ReleaseRevocation> findByReleaseId(Long releaseId);
}
