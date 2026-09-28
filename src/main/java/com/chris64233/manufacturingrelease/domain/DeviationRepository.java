package com.chris64233.manufacturingrelease.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviationRepository extends JpaRepository<Deviation, Long> {

    Optional<Deviation> findByDeviationNo(String deviationNo);

    boolean existsByDeviationNo(String deviationNo);
}
