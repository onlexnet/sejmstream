package onlexnet.infra.adapters.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import onlexnet.app.ports.out.SejmApiClient;

@Component
public class DefaultSejmApiClient implements SejmApiClient {

    // API docs: https://api.sejm.gov.pl/sejm.html
    private final RestClient restClient = RestClient.create("https://api.sejm.gov.pl");

    @Override
    public List<SejmTerm> fetchTerms() {
        return this.restClient.get()
                .uri("sejm/term")
                .retrieve()
                .body(new ParameterizedTypeReference<List<SejmTerm>>() {
                });
    }

    @Override
    public List<VotingItem> fetchVotingsForDate(int termNum, LocalDate date) {
        return this.restClient.get()
                .uri("sejm/term{termNum}/votings/search?dateFrom={dateFrom}&dateTo={dateTo}", termNum, date, date)
                .retrieve()
                .body(new ParameterizedTypeReference<List<VotingItem>>() {
                });
    }

    @Override
    public List<CommitteeSittingItem> fetchCommitteeSittingsForDate(int termNum, LocalDate date) {
        return this.restClient.get()
                .uri("sejm/term{termNum}/committees/sittings/{date}", termNum, date)
                .retrieve()
                .body(new ParameterizedTypeReference<List<CommitteeSittingItem>>() {
                });
    }

    @Override
    public List<PrintItem> fetchPrintsModifiedSince(int termNum, LocalDate since) {
        var prints = this.restClient.get()
                .uri("sejm/term{termNum}/prints?sort_by=-lastModified&limit=100", termNum)
                .retrieve()
                .body(new ParameterizedTypeReference<List<PrintItem>>() {
                });

        return prints.stream()
                .filter(print -> print.changeDate().toLocalDate().isEqual(since)
                        || print.changeDate().toLocalDate().isAfter(since))
                .toList();
    }

    @Override
    public List<InterpellationItem> fetchInterpellationsModifiedSince(int termNum, LocalDateTime since) {
        var formattedSince = since.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        return this.restClient.get()
                .uri("sejm/term{termNum}/interpellations?modifiedSince={since}&sort_by=-lastModified&limit=100", termNum,
                        formattedSince)
                .retrieve()
                .body(new ParameterizedTypeReference<List<InterpellationItem>>() {
                });
    }

    @Override
    public List<WrittenQuestionItem> fetchWrittenQuestionsModifiedSince(int termNum, LocalDateTime since) {
        var formattedSince = since.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        return this.restClient.get()
                .uri("sejm/term{termNum}/writtenQuestions?modifiedSince={since}&sort_by=-lastModified&limit=100", termNum,
                        formattedSince)
                .retrieve()
                .body(new ParameterizedTypeReference<List<WrittenQuestionItem>>() {
                });
    }

    @Override
    public List<BillItem> fetchBillsReceivedSince(int termNum, LocalDate since) {
        return this.restClient.get()
                .uri("sejm/term{termNum}/bills?dateOfReceiptFrom={since}&sort_by=-dateOfReceipt&limit=100", termNum, since)
                .retrieve()
                .body(new ParameterizedTypeReference<List<BillItem>>() {
                });
    }
}
