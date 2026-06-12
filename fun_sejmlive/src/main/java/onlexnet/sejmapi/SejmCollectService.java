package onlexnet.sejmapi;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.BillItem;
import onlexnet.app.ports.out.SejmApiClient.CommitteeSittingItem;
import onlexnet.app.ports.out.SejmApiClient.InterpellationItem;
import onlexnet.app.ports.out.SejmApiClient.PrintItem;
import onlexnet.app.ports.out.SejmApiClient.VotingItem;
import onlexnet.app.ports.out.SejmApiClient.WrittenQuestionItem;

@Service
public class SejmCollectService {

    private static final String DATA_TYPE_VOTING = "VOTING";
    private static final String DATA_TYPE_COMMITTEE_SITTING = "COMMITTEE_SITTING";
    private static final String DATA_TYPE_PRINT = "PRINT";
    private static final String DATA_TYPE_INTERPELLATION = "INTERPELLATION";
    private static final String DATA_TYPE_WRITTEN_QUESTION = "WRITTEN_QUESTION";
    private static final String DATA_TYPE_BILL = "BILL";

    private final SejmApiClient sejmApiClient;
    private final SejmDailyDigestRepository repository;
    private final ObjectMapper objectMapper;

    public SejmCollectService(final SejmApiClient sejmApiClient,
            final SejmDailyDigestRepository repository,
            final ObjectMapper objectMapper) {
        this.sejmApiClient = sejmApiClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public int collectVotings(final int termNum, final LocalDate date) {
        final List<VotingItem> items = this.sejmApiClient.fetchVotingsForDate(termNum, date);
        return upsertItems(date, DATA_TYPE_VOTING, items, this::votingKey, VotingItem::topic);
    }

    public int collectCommitteeSittings(final int termNum, final LocalDate date) {
        final List<CommitteeSittingItem> items = this.sejmApiClient.fetchCommitteeSittingsForDate(termNum, date);
        return upsertItems(date, DATA_TYPE_COMMITTEE_SITTING, items, this::committeeSittingKey, CommitteeSittingItem::agenda);
    }

    public int collectPrints(final int termNum, final LocalDate date) {
        final List<PrintItem> items = this.sejmApiClient.fetchPrintsModifiedSince(termNum, date);
        return upsertItems(date, DATA_TYPE_PRINT, items, this::printKey, PrintItem::title);
    }

    public int collectInterpellations(final int termNum, final LocalDate date) {
        final List<InterpellationItem> items = this.sejmApiClient.fetchInterpellationsModifiedSince(termNum,
                date.atStartOfDay());
        return upsertItems(date, DATA_TYPE_INTERPELLATION, items, this::interpellationKey, InterpellationItem::title);
    }

    public int collectWrittenQuestions(final int termNum, final LocalDate date) {
        final List<WrittenQuestionItem> items = this.sejmApiClient.fetchWrittenQuestionsModifiedSince(termNum,
                date.atStartOfDay());
        return upsertItems(date, DATA_TYPE_WRITTEN_QUESTION, items, this::writtenQuestionKey, WrittenQuestionItem::title);
    }

    public int collectBills(final int termNum, final LocalDate date) {
        final List<BillItem> items = this.sejmApiClient.fetchBillsReceivedSince(termNum, date);
        return upsertItems(date, DATA_TYPE_BILL, items, this::billKey, BillItem::title);
    }

    private <T> int upsertItems(final LocalDate date,
            final String dataType,
            final List<T> items,
            final ItemKeyResolver<T> keyResolver,
            final ItemTitleResolver<T> titleResolver) {

        int processed = 0;
        for (T item : items) {
            final String payloadJson = writeJson(item);
            final String itemKey = keyResolver.resolve(item);
            final String itemTitle = nullableText(titleResolver.resolve(item));
            this.repository.upsertItem(date, dataType, itemKey, itemTitle, payloadJson);
            processed++;
        }
        return processed;
    }

    private String votingKey(final VotingItem item) {
        return requiredNumber(item.sitting(), "sitting") + "/" + requiredNumber(item.votingNumber(), "votingNumber");
    }

    private String committeeSittingKey(final CommitteeSittingItem item) {
        return requiredText(item.code(), "code") + "/" + requiredNumber(item.num(), "num");
    }

    private String printKey(final PrintItem item) {
        return requiredText(item.number(), "number");
    }

    private String interpellationKey(final InterpellationItem item) {
        return requiredNumber(item.num(), "num");
    }

    private String writtenQuestionKey(final WrittenQuestionItem item) {
        return requiredNumber(item.num(), "num");
    }

    private String billKey(final BillItem item) {
        return requiredText(item.number(), "number");
    }

    private String requiredText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing field '" + fieldName + "' required for digest item key.");
        }
        return value;
    }

    private String requiredNumber(final int value, final String fieldName) {
        if (value <= 0) {
            throw new IllegalStateException("Missing field '" + fieldName + "' required for digest item key.");
        }
        return Integer.toString(value);
    }

    private String nullableText(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String writeJson(final Object item) {
        try {
            return this.objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize digest item payload.", exception);
        }
    }

    @FunctionalInterface
    private interface ItemKeyResolver<T> {
        String resolve(T item);
    }

    @FunctionalInterface
    private interface ItemTitleResolver<T> {
        String resolve(T item);
    }
}
