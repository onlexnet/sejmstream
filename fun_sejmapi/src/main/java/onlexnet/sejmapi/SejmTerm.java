package onlexnet.sejmapi;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents one Sejm term entry returned by the live {@code /sejm/term} API.
 */
public record SejmTerm(
        boolean current,
        LocalDate from,
        int num,
        SejmPrints prints,
        LocalDate to) {
}

/**
 * Represents the nested prints summary for a Sejm term entry.
 */
record SejmPrints(int count, LocalDateTime lastChanged, String link) {
}
