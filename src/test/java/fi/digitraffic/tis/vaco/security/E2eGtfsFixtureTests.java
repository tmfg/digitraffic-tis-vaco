package fi.digitraffic.tis.vaco.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;

class E2eGtfsFixtureTests {

    @Test
    void fixtureContainsEveryRequiredGtfsFile() throws IOException {
        Set<String> names = new HashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/public/testfiles/vaco-e2e-gtfs.zip");
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }

        assertThat(names, hasItems("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar.txt"));
    }
}
