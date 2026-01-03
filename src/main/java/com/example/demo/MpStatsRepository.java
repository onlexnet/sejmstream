package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MpStatsRepository extends JpaRepository<MpStats, Long> {
    Optional<MpStats> findByMpId(int mpId);
}
