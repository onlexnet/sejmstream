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

    record VotingItem(
        LocalDateTime date,
        int sitting,
        int votingNumber,
        String topic,
        int yes,
        int no,
        int abstain,
        int totalVoted,
        int notParticipating) {
    }

    record CommitteeSittingItem(
        String code,
        LocalDate date,
        int num,
        String agenda,
        String status,
        String room) {
    }

    record PrintItem(
        String number,
        String title,
        LocalDateTime changeDate,
        String deliveryDate) {
    }

    /**
     * Represents the author of a reply.
     * Use {@link Known} when the author name is present, {@link Unknown} when the API did not provide it.
     */
    sealed interface ReplyFrom permits ReplyFrom.Known, ReplyFrom.Unknown {
        /** The author name is known. */
        record Known(String value) implements ReplyFrom {}
        /** The author name was absent in the API response. */
        record Unknown() implements ReplyFrom {}
    }

    /** A single reply received by the Sejm in response to an interpellation. */
    record ReplyItem(
        String key,
        ReplyFrom from,
        LocalDate receiptDate) {
    }

    record InterpellationItem(
        int num,
        String title,
        List<String> to,
        String sentDate,
        String lastModified,
        List<ReplyItem> replies) {
    }

    record WrittenQuestionItem(
        int num,
        String title,
        List<String> to,
        String sentDate,
        String lastModified) {
    }

    record BillItem(
        String number,
        String title,
        String dateOfReceipt,
        String submissionType,
        String status) {
    }

    /**
     * Fetches the current list of Sejm terms.
     *
     * @return list of Sejm terms
     */
    List<SejmTerm> fetchTerms();

    List<VotingItem> fetchVotingsForDate(int termNum, LocalDate date);

    List<CommitteeSittingItem> fetchCommitteeSittingsForDate(int termNum, LocalDate date);

    List<PrintItem> fetchPrintsModifiedSince(int termNum, LocalDate since);

    List<InterpellationItem> fetchInterpellationsModifiedSince(int termNum, LocalDateTime since);

    List<WrittenQuestionItem> fetchWrittenQuestionsModifiedSince(int termNum, LocalDateTime since);

    List<BillItem> fetchBillsReceivedSince(int termNum, LocalDate since);
}

