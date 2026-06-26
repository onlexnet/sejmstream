package onlexnet.infra.adapters.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.text.DateFormat;
import java.util.List;
import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.infra.adapters.out.sejm.generated.api.BillsApi;
import onlexnet.infra.adapters.out.sejm.generated.api.CommitteesApi;
import onlexnet.infra.adapters.out.sejm.generated.api.DefaultApi;
import onlexnet.infra.adapters.out.sejm.generated.api.InterpellationsApi;
import onlexnet.infra.adapters.out.sejm.generated.api.PrintsApi;
import onlexnet.infra.adapters.out.sejm.generated.api.VotingsApi;
import onlexnet.infra.adapters.out.sejm.generated.api.WrittenQuestionsApi;
import onlexnet.infra.adapters.out.sejm.generated.core.ApiClient;
import onlexnet.infra.adapters.out.sejm.generated.model.Bill;
import onlexnet.infra.adapters.out.sejm.generated.model.CommitteeSitting;
import onlexnet.infra.adapters.out.sejm.generated.model.Interpellation;
import onlexnet.infra.adapters.out.sejm.generated.model.Print;
import onlexnet.infra.adapters.out.sejm.generated.model.PrintInfo;
import onlexnet.infra.adapters.out.sejm.generated.model.Term;
import onlexnet.infra.adapters.out.sejm.generated.model.Voting;
import onlexnet.infra.adapters.out.sejm.generated.model.WrittenQuestion;

@Component
final class DefaultSejmApiClient implements SejmApiClient {

        private static final String DEFAULT_API_BASE_PATH = "https://api.sejm.gov.pl";
        private static final int DEFAULT_LIMIT = 100;
        private static final DateTimeFormatter API_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        private final DefaultApi defaultApi;
        private final VotingsApi votingsApi;
        private final CommitteesApi committeesApi;
        private final PrintsApi printsApi;
        private final InterpellationsApi interpellationsApi;
        private final WrittenQuestionsApi writtenQuestionsApi;
        private final BillsApi billsApi;

        public DefaultSejmApiClient() {
                this(RestClient.builder(), DEFAULT_API_BASE_PATH);
        }

        public DefaultSejmApiClient(
                        final RestClient.Builder restClientBuilder,
                        @Value("${sejm.api.base-path:https://api.sejm.gov.pl}") final String basePath) {
                this(buildApiClient(restClientBuilder, basePath));
        }

        DefaultSejmApiClient(ApiClient apiClient) {
                var nonNullApiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
                this.defaultApi = new DefaultApi(nonNullApiClient);
                this.votingsApi = new VotingsApi(nonNullApiClient);
                this.committeesApi = new CommitteesApi(nonNullApiClient);
                this.printsApi = new PrintsApi(nonNullApiClient);
                this.interpellationsApi = new InterpellationsApi(nonNullApiClient);
                this.writtenQuestionsApi = new WrittenQuestionsApi(nonNullApiClient);
                this.billsApi = new BillsApi(nonNullApiClient);
        }

    @Override
    public List<SejmTerm> fetchTerms() {
                var terms = callSejmApi("DefaultApi.sejmTermGet", this.defaultApi::sejmTermGet);
                return nullSafe(terms).stream().map(this::mapTerm).toList();
    }

    @Override
        public List<VotingItem> fetchVotingsForDate(final int termNum, final LocalDate date) {
                var votings = callSejmApi(
                                "VotingsApi.sejmTermtermVotingsSearchGet",
                                () -> this.votingsApi.sejmTermtermVotingsSearchGet(termNum, date, date, DEFAULT_LIMIT, 0, null, null));
                return nullSafe(votings).stream().map(this::mapVoting).toList();
    }

    @Override
        public List<CommitteeSittingItem> fetchCommitteeSittingsForDate(final int termNum, final LocalDate date) {
                var sittings = callSejmApi(
                                "CommitteesApi.sejmTermtermCommitteesSittingsDateGet",
                                () -> this.committeesApi.sejmTermtermCommitteesSittingsDateGet(date, termNum, null));
                return nullSafe(sittings).stream().map(this::mapCommitteeSitting).toList();
    }

    @Override
        public List<PrintItem> fetchPrintsModifiedSince(final int termNum, final LocalDate since) {
                var prints = callSejmApi(
                        "PrintsApi.sejmTermtermPrintsGet",
                        () -> this.printsApi.sejmTermtermPrintsGet(termNum, null, "-lastModified"));

                return nullSafe(prints).stream()
                                .filter(print -> print.getChangeDate() != null)
                                .filter(print -> {
                                        var changeDate = print.getChangeDate().toLocalDate();
                                        return changeDate.isEqual(since) || changeDate.isAfter(since);
                                })
                                .map(this::mapPrint)
                .toList();
    }

    @Override
        public List<InterpellationItem> fetchInterpellationsModifiedSince(final int termNum, final LocalDateTime since) {
                var interpellations = callSejmApi(
                                "InterpellationsApi.sejmTermtermInterpellationsGet",
                                () -> this.interpellationsApi.sejmTermtermInterpellationsGet(
                                                termNum,
                                                null,
                                                null,
                                                DEFAULT_LIMIT,
                                                since.atOffset(ZoneOffset.UTC),
                                                null,
                                                null,
                                                null,
                                                "-lastModified",
                                                null,
                                                null,
                                                null));

                return nullSafe(interpellations).stream().map(this::mapInterpellation).toList();
    }

    @Override
        public List<WrittenQuestionItem> fetchWrittenQuestionsModifiedSince(final int termNum, final LocalDateTime since) {
                var writtenQuestions = callSejmApi(
                                "WrittenQuestionsApi.sejmTermtermWrittenQuestionsGet",
                                () -> this.writtenQuestionsApi.sejmTermtermWrittenQuestionsGet(
                                                termNum,
                                                null,
                                                null,
                                                DEFAULT_LIMIT,
                                                since.atOffset(ZoneOffset.UTC),
                                                null,
                                                null,
                                                null,
                                                "-lastModified",
                                                null,
                                                null,
                                                null));

                return nullSafe(writtenQuestions).stream().map(this::mapWrittenQuestion).toList();
    }

    @Override
        public List<BillItem> fetchBillsReceivedSince(final int termNum, final LocalDate since) {
                var bills = callSejmApi(
                                "BillsApi.sejmTermtermBillsGet",
                                () -> this.billsApi.sejmTermtermBillsGet(
                                                termNum,
                                                null,
                                                since,
                                                null,
                                                null,
                                                DEFAULT_LIMIT,
                                                null,
                                                null,
                                                null,
                                                null,
                                                "-dateOfReceipt",
                                                null,
                                                null,
                                                null));

                return nullSafe(bills).stream().map(this::mapBill).toList();
        }

        private static ApiClient buildApiClient(final RestClient.Builder restClientBuilder, final String basePath) {
                var dateFormat = ApiClient.createDefaultDateFormat();
                var mapper = createDefaultMapper(dateFormat);

                var restClient = restClientBuilder
                                .configureMessageConverters(builder -> builder.addCustomConverter(
                                                new JacksonJsonHttpMessageConverter(mapper)))
                                .build();

                var apiClient = new ApiClient(restClient, mapper, dateFormat);
                apiClient.setBasePath(basePath);
                apiClient.setOffsetDateTimeFormatter(API_DATE_TIME_FORMATTER);
                return apiClient;
        }

        private static JsonMapper createDefaultMapper(final DateFormat dateFormat) {
                var customModule = new SimpleModule();
                customModule.addDeserializer(OffsetDateTime.class, new LenientOffsetDateTimeDeserializer());
                return JsonMapper.builder()
                                .defaultDateFormat(dateFormat)
                                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                .addModule(customModule)
                                .build();
        }

        private SejmTerm mapTerm(final Term term) {
                var prints = term.getPrints();
                return new SejmTerm(
                                Boolean.TRUE.equals(term.getCurrent()),
                                term.getFrom(),
                                intOrZero(term.getNum()),
                                mapPrints(prints),
                                term.getTo());
        }

        private SejmPrints mapPrints(final PrintInfo prints) {
                if (prints == null) {
                        return null;
                }
                return new SejmPrints(
                                intOrZero(prints.getCount()),
                                toLocalDateTime(prints.getLastChanged()),
                                prints.getLink());
        }

        private VotingItem mapVoting(final Voting voting) {
                return new VotingItem(
                                toLocalDateTime(voting.getDate()),
                                intOrZero(voting.getSitting()),
                                intOrZero(voting.getVotingNumber()),
                                firstPresent(voting.getTopic(), voting.getTitle(), voting.getDescription()),
                                intOrZero(voting.getYes()),
                                intOrZero(voting.getNo()),
                                intOrZero(voting.getAbstain()),
                                intOrZero(voting.getTotalVoted()),
                                intOrZero(voting.getNotParticipating()));
        }

        private CommitteeSittingItem mapCommitteeSitting(final CommitteeSitting sitting) {
                return new CommitteeSittingItem(
                                sitting.getCode(),
                                sitting.getDate(),
                                intOrZero(sitting.getNum()),
                                sitting.getAgenda(),
                                sitting.getStatus() == null ? null : sitting.getStatus().getValue(),
                                sitting.getRoom());
        }

        private PrintItem mapPrint(final Print print) {
                return new PrintItem(
                                print.getNumber(),
                                print.getTitle(),
                                toLocalDateTime(print.getChangeDate()),
                                print.getDeliveryDate() == null ? null : print.getDeliveryDate().toString());
        }

        private InterpellationItem mapInterpellation(final Interpellation interpellation) {
                return new InterpellationItem(
                                intOrZero(interpellation.getNum()),
                                interpellation.getTitle(),
                                nullSafe(interpellation.getTo()),
                                interpellation.getSentDate() == null ? null : interpellation.getSentDate().toString(),
                                interpellation.getLastModified() == null ? null : interpellation.getLastModified().toString());
        }

        private WrittenQuestionItem mapWrittenQuestion(final WrittenQuestion question) {
                return new WrittenQuestionItem(
                                intOrZero(question.getNum()),
                                question.getTitle(),
                                nullSafe(question.getTo()),
                                question.getSentDate() == null ? null : question.getSentDate().toString(),
                                question.getLastModified() == null ? null : question.getLastModified().toString());
        }

        private BillItem mapBill(final Bill bill) {
                return new BillItem(
                                bill.getNumber(),
                                bill.getTitle(),
                                bill.getDateOfReceipt() == null ? null : bill.getDateOfReceipt().toString(),
                                bill.getSubmissionType() == null ? null : bill.getSubmissionType().toString(),
                                bill.getStatus() == null ? null : bill.getStatus().toString());
        }

        private static LocalDateTime toLocalDateTime(final OffsetDateTime value) {
                return value == null ? null : value.toLocalDateTime();
        }

        private static int intOrZero(final Integer value) {
                return value == null ? 0 : value;
        }

        private static <T> List<T> nullSafe(final List<T> value) {
                return value == null ? List.of() : value;
        }

        private static String firstPresent(final String... candidates) {
                for (var candidate : candidates) {
                        if (candidate != null && !candidate.isBlank()) {
                                return candidate;
                        }
                }
                return null;
    }

        private <T> T callSejmApi(final String operationName, final ApiCall<T> call) {
                try {
                        return call.execute();
                } catch (RestClientException exception) {
                        throw new IllegalStateException("Sejm API call failed for " + operationName, exception);
                }
        }

        @FunctionalInterface
        private interface ApiCall<T> {
                T execute();
        }

        private static final class LenientOffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

                @Override
                public OffsetDateTime deserialize(final JsonParser parser, final DeserializationContext context)
                                                throws JacksonException {
                        var textValue = parser.getValueAsString();
                        if (textValue == null || textValue.isBlank()) {
                                return null;
                        }

                        try {
                                return OffsetDateTime.parse(textValue);
                        } catch (DateTimeParseException ignored) {
                                return LocalDateTime.parse(textValue, API_DATE_TIME_FORMATTER).atOffset(ZoneOffset.UTC);
                        }
                }
        }
}
