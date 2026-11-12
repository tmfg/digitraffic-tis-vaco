package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.ImmutableCompanyLatestEntry;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

class AdminToolsServiceCsvTests {

    private static final int FEED_NAME_COLUMN = 4;
    private static final int URL_COLUMN = 3;
    private static final int CONVERTED_FORMAT_COLUMN = 6;
    private static final String FEED_URL = "https://example.fi/feed.zip";
    private static final int CONTEXT_COLUMN = 2;
    private static final int ENTRY_ID_COLUMN = 9;

    private final AdminToolsRepository repository = Mockito.mock(AdminToolsRepository.class);
    private final AdminToolsService service = new AdminToolsService(repository, Mockito.mock(MeService.class));

    @Test
    void neutralizesCellsThatStartWithAFormulaCharacter() throws IOException {
        Mockito.when(repository.getDataDeliveryOverview(null)).thenReturn(List.of(entry("=HYPERLINK(\"http://evil\")", "+1")));

        List<CSVRecord> records = export();

        assertThat(records.get(0).get(FEED_NAME_COLUMN), equalTo("'=HYPERLINK(\"http://evil\")"));
        assertThat(records.get(0).get(CONTEXT_COLUMN), equalTo("'+1"));
    }

    @Test
    void neutralizesTheOtherFormulaCharacters() throws IOException {
        Mockito.when(repository.getDataDeliveryOverview(null)).thenReturn(List.of(
            entry("-2+3", "ctx"),
            entry("@SUM(1)", "ctx"),
            entry("\tcmd", "ctx"),
            entry("\rcmd", "ctx")));

        List<CSVRecord> records = export();

        assertThat(records.get(0).get(FEED_NAME_COLUMN), equalTo("'-2+3"));
        assertThat(records.get(1).get(FEED_NAME_COLUMN), equalTo("'@SUM(1)"));
        assertThat(records.get(2).get(FEED_NAME_COLUMN), equalTo("'\tcmd"));
        assertThat(records.get(3).get(FEED_NAME_COLUMN), equalTo("'\rcmd"));
    }

    @Test
    void keepsOrdinaryCellsAndEmptyValuesUnchanged() throws IOException {
        Mockito.when(repository.getDataDeliveryOverview(null)).thenReturn(List.of(entry("Bus feed - north", null)));

        List<CSVRecord> records = export();

        assertThat(records.get(0).get(FEED_NAME_COLUMN), equalTo("Bus feed - north"));
        assertThat(records.get(0).get(CONTEXT_COLUMN), equalTo(""));
        assertThat(records.get(0).get(ENTRY_ID_COLUMN), equalTo("entry-1"));
    }

    @Test
    void keepsTheBarePlaceholderAndPlainNumbersUnchanged() throws IOException {
        Mockito.when(repository.getDataDeliveryOverview(null)).thenReturn(List.of(
            entry("-", "-5"),
            entry("-1.5", "42")));

        List<CSVRecord> records = export();

        assertThat(records.get(0).get(FEED_NAME_COLUMN), equalTo("-"));
        assertThat(records.get(0).get(CONTEXT_COLUMN), equalTo("-5"));
        assertThat(records.get(1).get(FEED_NAME_COLUMN), equalTo("-1.5"));
        assertThat(records.get(1).get(CONTEXT_COLUMN), equalTo("42"));
    }

    @Test
    void keepsTheConvertedFormatPlaceholderAndUrlsUnchanged() throws IOException {
        Mockito.when(repository.getDataDeliveryOverview(null)).thenReturn(List.of(
            ImmutableCompanyLatestEntry.builder()
                .companyName("Company").businessId("1234567-8").publicId("entry-1").feedName("feed")
                .url(FEED_URL).format("gtfs").convertedFormat("-").build()));

        List<CSVRecord> records = export();

        assertThat(records.get(0).get(URL_COLUMN), equalTo(FEED_URL));
        assertThat(records.get(0).get(CONVERTED_FORMAT_COLUMN), equalTo("-"));
    }

    @Test
    void neutralizesEveryFreeTextColumn() throws IOException {
        Mockito.when(repository.getDataDeliveryOverview(null)).thenReturn(List.of(
            ImmutableCompanyLatestEntry.builder()
                .companyName("=company").businessId("=business").publicId("=public").feedName("=feed")
                .context("=context").url("=url").format("=format").convertedFormat("=converted").build()));

        CSVRecord record = export().get(0);

        for (int column : new int[] {0, 1, CONTEXT_COLUMN, URL_COLUMN, FEED_NAME_COLUMN, 5,
                                     CONVERTED_FORMAT_COLUMN, ENTRY_ID_COLUMN}) {
            assertThat("column " + column, record.get(column), startsWith("'="));
        }
    }

    private List<CSVRecord> export() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportDataDeliveryToCsv(out, "en", false);
        String csv = out.toString(Charset.defaultCharset());
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
            .parse(new StringReader(csv)).getRecords();
    }

    private static CompanyLatestEntry entry(String feedName, String context) {
        return ImmutableCompanyLatestEntry.builder()
            .companyName("Company")
            .businessId("1234567-8")
            .publicId("entry-1")
            .feedName(feedName)
            .context(context)
            .url(FEED_URL)
            .format("gtfs")
            .build();
    }
}
