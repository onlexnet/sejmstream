package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.SejmApiClient.BillItem;
import onlexnet.app.ports.out.SejmApiClient.CommitteeSittingItem;
import onlexnet.app.ports.out.SejmApiClient.InterpellationItem;
import onlexnet.app.ports.out.SejmApiClient.PrintItem;
import onlexnet.app.ports.out.SejmApiClient.VotingItem;
import onlexnet.app.ports.out.SejmApiClient.WrittenQuestionItem;

class SejmDigestServiceTest {

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 13);

    private SejmDigestService service;
    private FakeSejmDailyDigestRepository repository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.repository = new FakeSejmDailyDigestRepository();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.service = new SejmDigestService(this.repository, this.objectMapper);
    }

    @Test
    void givenRowsForAllDataTypes_whenBuildingDigest_thenReturnsPolishPostWithSections() throws Exception {
        var rows = List.of(
                row("VOTING", this.objectMapper.writeValueAsString(new VotingItem(
                        LocalDateTime.of(2026, 6, 13, 10, 0),
                        1,
                        25,
                        "Głosowanie nad ustawą",
                        220,
                        180,
                        20,
                        420,
                        40))),
                row("COMMITTEE_SITTING", this.objectMapper.writeValueAsString(new CommitteeSittingItem(
                        "KOMINF",
                        TEST_DATE,
                        12,
                        "<p>Omówienie projektu i harmonogramu</p>",
                        "ODBYTA",
                        "101"))),
                row("PRINT", this.objectMapper.writeValueAsString(new PrintItem(
                        "1234",
                        "Projekt ustawy budżetowej",
                        LocalDateTime.of(2026, 6, 13, 8, 30),
                        "2026-06-13"))),
                row("INTERPELLATION", this.objectMapper.writeValueAsString(new InterpellationItem(
                        777,
                        "Interpelacja w sprawie transportu",
                        List.of("Minister Infrastruktury"),
                        "2026-06-13",
                        "2026-06-13T08:00:00"))),
                row("WRITTEN_QUESTION", this.objectMapper.writeValueAsString(new WrittenQuestionItem(
                        888,
                        "Zapytanie o finansowanie szpitali",
                        List.of("Minister Zdrowia"),
                        "2026-06-13",
                        "2026-06-13T09:00:00"))),
                row("BILL", this.objectMapper.writeValueAsString(new BillItem(
                        "UC-44",
                        "Projekt ustawy o jawności",
                        "2026-06-13",
                        "Rządowy",
                        "W toku"))));

        this.repository.setRows(rows);

        var digest = this.service.buildDigest(TEST_DATE);

        assertThat(digest).isPresent();
        assertThat(digest.orElseThrow())
                .contains("🏛️ Dzisiaj w Sejmie (2026-06-13):")
                .contains("📊 GŁOSOWANIA (1):")
                .contains("📋 KOMISJE (1):")
                .contains("Omówienie projektu i harmonogramu")
                .contains("📄 DRUKI (1):")
                .contains("🗣️ INTERPELACJE (1):")
                .contains("❓ ZAPYTANIA (1):")
                .contains("📝 PROJEKTY (1):")
                .contains("#SejmStream #Sejm #ParlamentPolski");
    }

    @Test
    void givenNoRows_whenBuildingDigest_thenReturnsEmptyOptional() {
        this.repository.setRows(List.of());

        var digest = this.service.buildDigest(TEST_DATE);

        assertThat(digest).isEmpty();
    }

    @Test
    void givenMoreThanFiveItemsInSection_whenBuildingDigest_thenTruncatesAndShowsOverflowCount()
            throws Exception {
        var rows = new ArrayList<Map<String, Object>>();
        for (var index = 1; index <= 7; index++) {
            rows.add(row("VOTING", this.objectMapper.writeValueAsString(new VotingItem(
                    LocalDateTime.of(2026, 6, 13, 10, index),
                    1,
                    index,
                    "Temat " + index,
                    200 + index,
                    100,
                    10,
                    310,
                    0))));
        }

        this.repository.setRows(rows);

        var digest = this.service.buildDigest(TEST_DATE);

        assertThat(digest).isPresent();
        assertThat(digest.orElseThrow())
                .contains("📊 GŁOSOWANIA (7):")
                .contains("• Temat 1")
                .contains("• Temat 5")
                .doesNotContain("• Temat 6")
                .doesNotContain("• Temat 7")
                .contains("... i 2 więcej");
    }

    @Test
    void givenMalformedRowAndValidRow_whenBuildingDigest_thenSkipsMalformedAndBuildsDigest()
            throws Exception {
        this.repository.setRows(List.of(
                row("VOTING", "{not valid json}"),
                row("VOTING", this.objectMapper.writeValueAsString(new VotingItem(
                        LocalDateTime.of(2026, 6, 13, 10, 0),
                        1,
                        25,
                        "Poprawny temat",
                        220,
                        180,
                        20,
                        420,
                        40)))));

        var digest = this.service.buildDigest(TEST_DATE);

        assertThat(digest).isPresent();
        assertThat(digest.orElseThrow())
                .contains("📊 GŁOSOWANIA (1):")
                .contains("Poprawny temat");
    }

    @Test
    void givenMissingDataType_whenBuildingDigest_thenSkipsRowAndReturnsEmpty() throws Exception {
        this.repository.setRows(List.of(Map.of("item_json", this.objectMapper.writeValueAsString(new VotingItem(
                LocalDateTime.of(2026, 6, 13, 10, 0),
                1,
                25,
                "Temat",
                220,
                180,
                20,
                420,
                40)))));

        var digest = this.service.buildDigest(TEST_DATE);

        assertThat(digest).isEmpty();
    }

    private static Map<String, Object> row(final String dataType, final String json) {
        var row = new LinkedHashMap<String, Object>();
        row.put("data_type", dataType);
        row.put("item_json", json);
        return row;
    }

    private static final class FakeSejmDailyDigestRepository extends SejmDailyDigestRepository {

        private List<Map<String, Object>> rows = List.of();

        private FakeSejmDailyDigestRepository() {
            super(null);
        }

        @Override
        public List<Map<String, Object>> findByDate(final LocalDate date) {
            return this.rows;
        }

        private void setRows(final List<Map<String, Object>> rows) {
            this.rows = rows;
        }
    }
}
