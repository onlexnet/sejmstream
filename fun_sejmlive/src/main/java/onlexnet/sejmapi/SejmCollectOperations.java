package onlexnet.sejmapi;

import java.time.LocalDate;

/**
 * Collection operations for ingesting Sejm activity into the daily digest store.
 */
public interface SejmCollectOperations {

    int collectVotings(int termNum, LocalDate date);

    int collectCommitteeSittings(int termNum, LocalDate date);

    int collectPrints(int termNum, LocalDate date);

    int collectInterpellations(int termNum, LocalDate date);

    int collectWrittenQuestions(int termNum, LocalDate date);

    int collectBills(int termNum, LocalDate date);
}