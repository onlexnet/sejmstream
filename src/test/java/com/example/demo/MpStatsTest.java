package com.example.demo;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class MpStatsTest {
    @Test
    //checks if attendance is calculated correctly
    public void testAttendanceCalculation(){
        MpStats mpStats = new MpStats(1,"Andrzej Adamczyk");

        mpStats.addVote(true);
        mpStats.addVote(false);
        mpStats.addVote(true);


        double attendance = mpStats.getAttendance();
        Assertions.assertThat(attendance).isEqualTo(2.0/3.0);
    }

    @Test
    //checks if without any attendances activity is treated as full attendance
    public void testZeroAttendance(){
        MpStats mpStats = new MpStats(2,"Beata Szydlo");

        double attendance = mpStats.getAttendance();
        Assertions.assertThat(attendance).isEqualTo(1.0);
    }
}
