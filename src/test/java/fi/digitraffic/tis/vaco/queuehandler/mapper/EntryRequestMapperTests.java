package fi.digitraffic.tis.vaco.queuehandler.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.utilities.Strings;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class EntryRequestMapperTests {

    private EntryRequestMapper entryRequestMapper;
    private ImmutableEntryRequest entryRequest;

    @BeforeEach
    void setUp() {
        entryRequestMapper = new EntryRequestMapper(new ObjectMapper());
        // entry request with all required fields
        entryRequest = ImmutableEntryRequest.builder()
            .format("gtfs")
            .url("http://fintraffic.example")
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID)
            .build();
    }

    @Test
    void stripsExtraQuotesFromEtagIfPresent() {
        entryRequest = entryRequest.withEtag("\"double quotes!\"");
        ImmutableEntry entry = entryRequestMapper.toEntry(entryRequest);

        assertThat(entry.etag(), equalTo("double quotes!"));
    }

    @Test
    void stripsUnicodeByteOrderMarkFromAllStrings() {
        entryRequest = entryRequest
            .withUrl(Strings.BOM_UTF_32_BE + "http://fintraffic.suomi")
            .withEtag(Strings.BOM_UTF_16_BE + "etag")
            .withFormat(Strings.BOM_UTF_8 + "netex");
        ImmutableEntry entry = entryRequestMapper.toEntry(entryRequest);

        assertThat(entry.etag(), equalTo("etag"));
        assertThat(entry.url(), equalTo("http://fintraffic.suomi"));
        assertThat(entry.format(), equalTo("netex"));
    }
}