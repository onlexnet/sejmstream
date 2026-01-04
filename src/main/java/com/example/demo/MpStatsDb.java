package com.example.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "mp_stats")
public class MpStatsDb {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mp_id")
    private int mpId;

    @Column(name = "first_last_name")
    private String firstLastName;

    @Column(name = "total_votings")
    private int totalVotings = 0;

    @Column(name = "present_count")
    private int presentCount = 0;

 
}
