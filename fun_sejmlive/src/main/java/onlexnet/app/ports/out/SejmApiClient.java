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
     * A single entry in the replies list for an interpellation.
     * Use {@link ActualReply} for a real reply, {@link Prolongation} when the ministry requested a deadline extension.
     */
    sealed interface ReplyItem permits ReplyItem.ActualReply, ReplyItem.Prolongation {
        /** A substantive reply with a document key, author and receipt date. */
        record ActualReply(String key, String from, LocalDate receiptDate) implements ReplyItem {}
        /** The ministry requested a deadline extension — no reply document exists. */
        record Prolongation(String from) implements ReplyItem {}
    }

    sealed interface InterpellationLinks permits InterpellationLinks.Complete, InterpellationLinks.Missing {
        record Complete(
                String webDescription,
                String webBody,
                String body) implements InterpellationLinks {}

        record Missing() implements InterpellationLinks {}
    }

    record InterpellationItem(
        int num,
        String title,
        List<String> to,
        String sentDate,
        String lastModified,
        List<ReplyItem> replies,
        List<AttachmentMetadata> attachments,
        InterpellationLinks links) {

        public static InterpellationItem missing(
                final int num,
                final String title,
                final List<String> to,
                final String sentDate,
                final String lastModified,
                final List<ReplyItem> replies,
                final List<AttachmentMetadata> attachments) {
            return new InterpellationItem(
                    num,
                    title,
                    to,
                    sentDate,
                    lastModified,
                    replies,
                    attachments,
                    new InterpellationLinks.Missing());
        }
    }

        /**
         * Result of downloading and interpreting one interpellation attachment.
         */
        sealed interface AttachmentFetchResult
            permits AttachmentFetchResult.PdfText, AttachmentFetchResult.Unsupported, AttachmentFetchResult.Unavailable {

        /**
         * Attachment was recognized as PDF and text extraction succeeded.
         */
        record PdfText(
            String replyKey,
            String fileName,
            String mimeType,
            String text,
            int sizeBytes) implements AttachmentFetchResult {
        }

        /**
         * Attachment was fetched but is not supported by the current processing pipeline.
         */
        record Unsupported(
            String replyKey,
            String fileName,
            String mimeType,
            int sizeBytes,
            String reason) implements AttachmentFetchResult {
        }

        /**
         * Attachment could not be fetched or was empty.
         */
        record Unavailable(
            String replyKey,
            String fileName,
            String reason) implements AttachmentFetchResult {
        }
        }

        AttachmentFetchResult fetchAttachmentText(int termNum, String replyKey, String fileName);

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

