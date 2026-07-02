package onlexnet.app.ports.out;

import java.time.LocalDate;

/**
 * Output port for collecting Sejm activity and persisting it to the digest store.
 */
public interface SejmCollectOperations {

    int collectVotings(int termNum, LocalDate date);

    int collectCommitteeSittings(int termNum, LocalDate date);

    int collectPrints(int termNum, LocalDate date);

    int collectInterpellations(int termNum, LocalDate date);

    int collectWrittenQuestions(int termNum, LocalDate date);

    int collectBills(int termNum, LocalDate date);
}