package com.chris64233.manufacturingrelease.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.manufacturingrelease.domain.ReleaseRevocation;

public interface ReleaseRevocationRepository extends JpaRepository<ReleaseRevocation, Long> {

    List<ReleaseRevocation> findByReleaseIdOrderByRevokedAtDesc(Long releaseId);

    boolean existsByReleaseId(Long releaseId);
}
