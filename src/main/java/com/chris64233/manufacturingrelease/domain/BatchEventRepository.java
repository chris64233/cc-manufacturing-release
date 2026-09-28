package com.chris64233.manufacturingrelease.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchEventRepository extends JpaRepository<BatchEvent, Long> {
}
