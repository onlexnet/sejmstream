package onlexnet.app.ports.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Minimal Sejm API client used by the anonymous HTTP function.
 */
public interface SejmApiClient {

    /** Represents one Sejm term entry returned by the live {@code /sejm/term} API. */
    public record SejmTerm(
        boolean current,
        LocalDate from,
        int num,
        SejmPrints prints,
        LocalDate to) {
    }

    /** Represents the nested prints summary for a Sejm term entry. */
    record SejmPrints(int count, LocalDateTime lastChanged, String link) {
    }

    /**
     * Fetches the current list of Sejm terms.
     *
     * @return list of Sejm terms
     */
    List<SejmTerm> fetchTerms();
}

