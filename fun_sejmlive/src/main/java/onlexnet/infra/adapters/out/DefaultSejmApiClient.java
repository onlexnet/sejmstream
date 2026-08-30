package onlexnet.infra.adapters.out;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import onlexnet.app.ports.out.AttachmentMetadata;
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
import onlexnet.infra.adapters.out.sejm.generated.model.Reply;
import onlexnet.infra.adapters.out.sejm.generated.model.Term;
import onlexnet.infra.adapters.out.sejm.generated.model.Voting;
import onlexnet.infra.adapters.out.sejm.generated.model.WrittenQuestion;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@Component
class DefaultSejmApiClient implements SejmApiClient {

    private static final String DEFAULT_API_BASE_PATH = "https://api.sejm.gov.pl";
    private static final int DEFAULT_LIMIT = 100;
    private static final DateTimeFormatter API_DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DefaultApi defaultApi;
    private final HttpClient httpClient;
    private final String basePath;
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
            RestClient.Builder restClientBuilder,
            @Value("${sejm.api.base-path:https://api.sejm.gov.pl}") String basePath) {
        this(buildApiClient(restClientBuilder, basePath), HttpClient.newHttpClient(), basePath);
    }

    DefaultSejmApiClient(ApiClient apiClient) {
        this(apiClient, HttpClient.newHttpClient(), DEFAULT_API_BASE_PATH);
    }

    private DefaultSejmApiClient(ApiClient apiClient, HttpClient httpClient, String basePath) {
        this.defaultApi = new DefaultApi(apiClient);
        this.votingsApi = new VotingsApi(apiClient);
        this.committeesApi = new CommitteesApi(apiClient);
        this.printsApi = new PrintsApi(apiClient);
        this.interpellationsApi = new InterpellationsApi(apiClient);
        this.writtenQuestionsApi = new WrittenQuestionsApi(apiClient);
        this.billsApi = new BillsApi(apiClient);
        this.httpClient = httpClient;
        this.basePath = basePath;
    }

    @Override
    public List<SejmTerm> fetchTerms() {
        var terms = callSejmApi("DefaultApi.sejmTermGet", this.defaultApi::sejmTermGet);
        return nullSafe(terms).stream().map(this::mapTerm).toList();
    }

    @Override
    public List<VotingItem> fetchVotingsForDate(int termNum, LocalDate date) {
        var votings = callSejmApi(
                "VotingsApi.sejmTermtermVotingsSearchGet",
                () -> this.votingsApi.sejmTermtermVotingsSearchGet(termNum, date, date, DEFAULT_LIMIT, 0, null, null));
        return nullSafe(votings).stream().map(this::mapVoting).toList();
    }

    @Override
    public List<CommitteeSittingItem> fetchCommitteeSittingsForDate(int termNum, LocalDate date) {
        var sittings = callSejmApi(
                "CommitteesApi.sejmTermtermCommitteesSittingsDateGet",
                () -> this.committeesApi.sejmTermtermCommitteesSittingsDateGet(date, termNum, null));
        return nullSafe(sittings).stream().map(this::mapCommitteeSitting).toList();
    }

    @Override
    public List<PrintItem> fetchPrintsModifiedSince(int termNum, LocalDate since) {
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
    public List<InterpellationItem> fetchInterpellationsModifiedSince(int termNum, LocalDateTime since) {
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
    public List<WrittenQuestionItem> fetchWrittenQuestionsModifiedSince(int termNum, LocalDateTime since) {
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

    /**
     * Fetches an interpellation attachment and returns a JSON payload containing either extracted text
     * (for textual formats, including PDF) or Base64-encoded binary content.
     *
     * <p>Use this method when downstream processing needs normalized attachment content without
     * handling file download, MIME detection, PDF parsing, and binary/text branching separately.
     * The returned payload includes metadata (reply key, file name, MIME type, size) and content in
     * a single structure suitable for persistence or publishing.</p>
     *
     * @param termNum Sejm term number used to build the attachment endpoint.
     * @param replyKey reply identifier under which the attachment is exposed.
     * @param fileName attachment file name from the Sejm API.
     * @return serialized JSON payload with normalized attachment content, or {@code null} when the
     *         attachment is unavailable or empty.
     */
    @Override
    public AttachmentFetchResult fetchAttachmentText(int termNum, String replyKey, String fileName) {
        var safeReplyKey = encodePathSegment(replyKey);
        var safeFileName = encodePathSegment(fileName);
        var uri = URI.create(this.basePath + "/sejm/term" + termNum + "/interpellations/attachment/"
                + safeReplyKey + "/" + safeFileName);
        var request = HttpRequest.newBuilder(uri).GET().build();
        try {
            var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                return new AttachmentFetchResult.Unavailable(replyKey, fileName, "http-status-" + response.statusCode());
            }
            var body = response.body();
            if (body == null || body.length == 0) {
                return new AttachmentFetchResult.Unavailable(replyKey, fileName, "empty-body");
            }

            var mimeType = contentType(response);
            var extractedContent = extractTextContent(body, mimeType);
            return switch (extractedContent) {
                case PdfTextContent it -> new AttachmentFetchResult.PdfText(
                        replyKey,
                        fileName,
                        it.mimeType(),
                        it.text(),
                        body.length);
                case BinaryAttachmentContent it -> new AttachmentFetchResult.Unsupported(
                        replyKey,
                        fileName,
                        it.mimeType(),
                        body.length,
                        "unsupported-attachment-type");
            };
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to fetch attachment text", exception);
        }
    }

    @Override
    public List<BillItem> fetchBillsReceivedSince(int termNum, LocalDate since) {
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

    private static ApiClient buildApiClient(RestClient.Builder restClientBuilder, String basePath) {
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

    private static JsonMapper createDefaultMapper(DateFormat dateFormat) {
        var customModule = new SimpleModule();
        customModule.addDeserializer(OffsetDateTime.class, new LenientOffsetDateTimeDeserializer());
        return JsonMapper.builder()
                .defaultDateFormat(dateFormat)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(customModule)
                .build();
    }

    private static String contentType(HttpResponse<byte[]> response) {
        var rawValue = response.headers().firstValue("Content-Type").orElse("");
        var typeWithoutParams = rawValue.split(";", 2)[0].trim();
        if (typeWithoutParams.isBlank()) {
            return "application/octet-stream";
        }
        return typeWithoutParams.toLowerCase(Locale.ROOT);
    }

    private static AttachmentContent extractTextContent(byte[] body, String mimeType) {
        if (mimeType.equals("application/pdf")) {
            var extractedPdfText = extractPdfText(body);
            if (extractedPdfText != null) {
                return new PdfTextContent(mimeType, extractedPdfText);
            }
        }
        return new BinaryAttachmentContent(mimeType, body);
    }

    private static @Nullable String extractPdfText(byte[] pdfBytes) {
        try (var document = Loader.loadPDF(pdfBytes)) {
            var text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) {
                return null;
            }
            return text;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private SejmTerm mapTerm(Term term) {
        var prints = term.getPrints();
        return new SejmTerm(
                Boolean.TRUE.equals(term.getCurrent()),
                term.getFrom(),
                intOrZero(term.getNum()),
                mapPrints(prints),
                term.getTo());
    }

    private @Nullable SejmPrints mapPrints(PrintInfo prints) {
        if (prints == null) {
            return null;
        }
        return new SejmPrints(
                intOrZero(prints.getCount()),
                toLocalDateTime(prints.getLastChanged()),
                prints.getLink());
    }

    private VotingItem mapVoting(Voting voting) {
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

    private CommitteeSittingItem mapCommitteeSitting(CommitteeSitting sitting) {
        return new CommitteeSittingItem(
                sitting.getCode(),
                sitting.getDate(),
                intOrZero(sitting.getNum()),
                sitting.getAgenda(),
                sitting.getStatus() == null ? null : sitting.getStatus().getValue(),
                sitting.getRoom());
    }

    private PrintItem mapPrint(Print print) {
        return new PrintItem(
                print.getNumber(),
                print.getTitle(),
                toLocalDateTime(print.getChangeDate()),
                print.getDeliveryDate() == null ? null : print.getDeliveryDate().toString());
    }

    private InterpellationItem mapInterpellation(Interpellation interpellation) {
        var replies = nullSafe(interpellation.getReplies());
        var attachments = replies.stream()
                .flatMap(reply -> nullSafe(reply.getAttachments()).stream())
                .map(this::mapAttachment)
                .toList();
        var links = mapInterpellationLinks(interpellation);
        return new InterpellationItem(
                intOrZero(interpellation.getNum()),
                interpellation.getTitle(),
                nullSafe(interpellation.getTo()),
                interpellation.getSentDate() == null ? null : interpellation.getSentDate().toString(),
                interpellation.getLastModified() == null ? null : interpellation.getLastModified().toString(),
                replies.stream().map(this::mapReply).toList(),
                attachments,
                links);
    }

    private static SejmApiClient.InterpellationLinks mapInterpellationLinks(Interpellation interpellation) {
        var webDescription = findLinkHref(interpellation, "web-description");
        var webBody = findLinkHref(interpellation, "web-body");
        var body = findLinkHref(interpellation, "body");
        if (webDescription != null && webBody != null && body != null) {
            return new SejmApiClient.InterpellationLinks.Complete(webDescription, webBody, body);
        }
        return new SejmApiClient.InterpellationLinks.Missing();
    }

    private static @Nullable String findLinkHref(Interpellation interpellation, String rel) {
        var links = interpellation.getLinks();
        if (links == null || links.isEmpty()) {
            return null;
        }
        for (var link : links) {
            if (!(link instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            var relValue = map.get("rel");
            var hrefValue = map.get("href");
            if (relValue != null && relValue.toString().equals(rel) && hrefValue != null) {
                return hrefValue.toString();
            }
        }
        return null;
    }

    private AttachmentMetadata mapAttachment(onlexnet.infra.adapters.out.sejm.generated.model.Attachment attachment) {
        var name = attachment.getName();
        return new AttachmentMetadata(null, name, attachment.getURL(),
                attachment.getLastModified() == null ? null : attachment.getLastModified().toString(), name);
    }

    private ReplyItem mapReply(Reply reply) {
        var from = Objects.requireNonNull(reply.getFrom(), "ReplyItem.from must not be null");
        if (Boolean.TRUE.equals(reply.getProlongation())) {
            return new SejmApiClient.ReplyItem.Prolongation(from);
        }
        return new SejmApiClient.ReplyItem.ActualReply(
                Objects.requireNonNull(reply.getKey(), "ReplyItem.key must not be null"),
                from,
                Objects.requireNonNull(reply.getReceiptDate(), "ReplyItem.receiptDate must not be null"));
    }

    private WrittenQuestionItem mapWrittenQuestion(WrittenQuestion question) {
        return new WrittenQuestionItem(
                intOrZero(question.getNum()),
                question.getTitle(),
                nullSafe(question.getTo()),
                question.getSentDate() == null ? null : question.getSentDate().toString(),
                question.getLastModified() == null ? null : question.getLastModified().toString());
    }

    private BillItem mapBill(Bill bill) {
        return new BillItem(
                bill.getNumber(),
                bill.getTitle(),
                bill.getDateOfReceipt() == null ? null : bill.getDateOfReceipt().toString(),
                bill.getSubmissionType() == null ? null : bill.getSubmissionType().toString(),
                bill.getStatus() == null ? null : bill.getStatus().toString());
    }

    private static String encodePathSegment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static @Nullable LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static <T> List<T> nullSafe(@Nullable List<T> value) {
        return value == null ? List.of() : value;
    }

    private static @Nullable String firstPresent(String... candidates) {
        for (var candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private <T> @Nullable T callSejmApi(String operationName, ApiCall<T> call) {
        try {
            return call.execute();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Sejm API call failed for " + operationName, exception);
        }
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        @Nullable T execute();
    }

    private sealed interface AttachmentContent permits PdfTextContent, BinaryAttachmentContent {
    }

    private record PdfTextContent(String mimeType, String text) implements AttachmentContent {
    }

    private record BinaryAttachmentContent(String mimeType, byte[] content) implements AttachmentContent {
    }

    private static final class LenientOffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

        @Override
        public @Nullable OffsetDateTime deserialize(JsonParser parser, DeserializationContext context)
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
