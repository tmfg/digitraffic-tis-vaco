package fi.digitraffic.tis.vaco.summary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.repositories.SummaryRepository;
import fi.digitraffic.tis.vaco.exports.model.csv.CsvStructure;
import fi.digitraffic.tis.vaco.exports.model.csv.ImmutableCsvStructure;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.summary.model.ImmutableSummary;
import fi.digitraffic.tis.vaco.summary.model.RendererType;
import fi.digitraffic.tis.vaco.summary.model.gtfs.ImmutableGtfsInputSummary;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.function.Predicate.not;

@Service
public class GtfsInputSummaryService {

    private static final String COMPONENT_PRESENT_VALUE = "1";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    private final SummaryRepository summaryRepository;

    public GtfsInputSummaryService(SummaryRepository summaryRepository,
                                   ObjectMapper objectMapper) {
        this.summaryRepository = Objects.requireNonNull(summaryRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    private final CsvStructure agency = ImmutableCsvStructure.of(
        "agency.txt",
        List.of("agency_name", "agency_url", "agency_phone", "agency_email"));

    private final CsvStructure feedInfo = ImmutableCsvStructure.of(
        "feed_info.txt",
        List.of("feed_publisher_name",
            "feed_publisher_url",
            "feed_lang",
            "default_lang",
            "feed_start_date",
            "feed_end_date",
            "feed_version",
            "feed_contact_email",
            "feed_contact_uri"));

    private final CsvStructure routesCsv = ImmutableCsvStructure.of(
        "routes.txt",
        List.of("route_id",
            "route_short_name",
            "route_long_name",
            "route_color"));

    private final CsvStructure shapesCsv = ImmutableCsvStructure.of(
        "shapes.txt",
        List.of("shape_id"));

    private final CsvStructure stopsCsv = ImmutableCsvStructure.of(
        "stops.txt",
        List.of("stop_id", "location_type"));

    private final CsvStructure tripsCsv = ImmutableCsvStructure.of(
        "trips.txt",
        List.of("trip_id",
            "trip_headsign",
            "block_id",
            "wheelchair_accessible",
            "bikes_allowed"));

    private final CsvStructure transfersCsv = ImmutableCsvStructure.of(
        "transfers.txt",
        List.of());

    private final CsvStructure translationsCsv = ImmutableCsvStructure.of(
        "translations.txt",
        List.of());

    private Map<String, String> asMap(List<String> headers, CSVRecord csvRecord) {
        // while records don't need to be order dependent, LinkedHashMap makes logging and debugging easier
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach(header -> {
            if (csvRecord.isSet(header)) {
                result.put(header, csvRecord.get(header));
            }
        });
        return result;
    }

    public void generateGtfsInputSummaries(Entry entry, Task task, Path downloadedPackagePath) throws IOException {
        logger.info("Starting GTFS input summary generation for entry {} task {}", entry.publicId(), task.name());
        ImmutableGtfsInputSummary.Builder summaryBuilder = getEmptyGtfsSummaryObject();

        try (ZipFile zipFile = new ZipFile(downloadedPackagePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()) {
                    String zipEntryName = zipEntry.getName();
                    summaryBuilder = summaryBuilder.addFiles(zipEntryName);

                    try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                        if (agency.fileName().equals(zipEntryName)) {
                            summaryBuilder = processAgency(entry, task, inputStream, summaryBuilder);
                        } else if (feedInfo.fileName().equals(zipEntryName)) {
                            summaryBuilder = processFeedInfo(entry, task, inputStream, summaryBuilder);
                        } else if (routesCsv.fileName().equals(zipEntryName)) {
                            summaryBuilder = processRoutes(entry, task, inputStream, summaryBuilder);
                        } else if (shapesCsv.fileName().equals(zipEntryName)) {
                            summaryBuilder = processShapes(entry, task, inputStream, summaryBuilder);
                        } else if (stopsCsv.fileName().equals(zipEntryName)) {
                            summaryBuilder = processStops(entry, task, inputStream, summaryBuilder);
                        } else if (tripsCsv.fileName().equals(zipEntryName)) {
                            summaryBuilder = processTrips(entry, task, inputStream, summaryBuilder);
                        } else if ("transfers.txt".equals(zipEntryName)) {
                            summaryBuilder = processTransfers(entry, task, inputStream, summaryBuilder);
                        } else if ("translations.txt".equals(zipEntryName)) {
                            summaryBuilder = processTranslations(entry, task, inputStream, summaryBuilder);
                        } else {
                            logger.debug("Unhandled file {} in entry {} task {}", zipEntryName, entry.publicId(), task.name());
                        }
                    }
                }
            }
        }

        ImmutableGtfsInputSummary gtfsInputSummary = summaryBuilder.build();

        persistTaskSummaryItem(task, "files", RendererType.LIST, gtfsInputSummary.files());
        persistTaskSummaryItem(task, "counts", RendererType.LIST, gtfsInputSummary.counts());
        persistTaskSummaryItem(task, "components", RendererType.LIST, gtfsInputSummary.components());
    }

    private ImmutableGtfsInputSummary.Builder processAgency(Entry entry, Task task, InputStream inputStream, ImmutableGtfsInputSummary.Builder summaryBuilder) {
        List<Map<String, String>> list = processCsv(entry, task, inputStream, agency);
        persistTaskSummaryItem(task, "agencies", RendererType.CARD, list);
        return summaryBuilder.addCounts("Agencies: " + list.size());
    }

    private ImmutableGtfsInputSummary.Builder processFeedInfo(Entry entry, Task task, InputStream inputStream, ImmutableGtfsInputSummary.Builder summaryBuilder) {
        List<Map<String, String>> list = processCsv(entry, task, inputStream, feedInfo);
        persistTaskSummaryItem(task, "feedInfo", RendererType.CARD, list);
        return summaryBuilder.feedInfo(list).addComponents("Feed information");
    }

    private ImmutableGtfsInputSummary.Builder processRoutes(Entry entry, Task task, InputStream inputStream, ImmutableGtfsInputSummary.Builder summaryBuilder) {
        List<Map<String, String>> routes = processCsv(entry, task, inputStream, routesCsv);
        List<String> newComponents = new ArrayList<>();
        boolean colours = routes.stream()
            .anyMatch(route -> route.containsKey("route_color")
                && !route.get("route_color").isBlank());
        if (colours) {
            newComponents.add("Route Colors");
        }
        boolean names = routes.stream()
            .anyMatch(route -> hasValue(route, "route_short_name", not(String::isBlank))
                || hasValue(route, "route_long_name", not(String::isBlank)));
        if (names) {
            newComponents.add("Route Names");
        }

        return summaryBuilder
            .addCounts("Routes: " + routes.size())
            .addAllComponents(newComponents);
    }

    private ImmutableGtfsInputSummary.Builder processShapes(Entry entry, Task task, InputStream inputStream, ImmutableGtfsInputSummary.Builder summaryBuilder) {
        List<Map<String, String>> shapes = processCsv(entry, task, inputStream, shapesCsv);

        long uniqueShapesCount = shapes.stream().map(s -> s.get("shape_id")).distinct().count();
        summaryBuilder = summaryBuilder
            .addCounts("Shapes: " + uniqueShapesCount);

        if (!shapes.isEmpty()) {
            summaryBuilder = summaryBuilder.addComponents("Shapes");
        }
        return summaryBuilder;
    }

    private ImmutableGtfsInputSummary.Builder processStops(Entry entry, Task task, InputStream inputStream, ImmutableGtfsInputSummary.Builder summaryBuilder) {
        List<Map<String, String>> stops = processCsv(entry, task, inputStream, stopsCsv);

        List<String> components = new ArrayList<>();
        if (!stops.isEmpty() && stops.stream()
            .filter(stop -> hasValue(stop, "location_type", not(String::isBlank)))
            .map(s -> s.get("location_type")).distinct().findAny().isPresent()) {
            components.add("Location Types");
        }

        return summaryBuilder
            .addAllComponents(components)
            .addCounts("Stops: " + stops.size());
    }

    private ImmutableGtfsInputSummary.Builder processTrips(Entry entry, Task task, InputStream inputStream, ImmutableGtfsInputSummary.Builder summaryBuilder) {
        List<Map<String, String>> trips = processCsv(entry, task, inputStream, tripsCsv);

        summaryBuilder = summaryBuilder.addCounts("Trips: " +trips.size());

        if (!trips.isEmpty()) {
            List<String> newComponents = new ArrayList<>();
            List<String> newCounts = new ArrayList<>();

            boolean wheelchairAccessibility = trips.stream()
                .anyMatch(trip -> hasValue(trip, "wheelchair_accessible", COMPONENT_PRESENT_VALUE::equals));
            if (wheelchairAccessibility) {
                newComponents.add("Wheelchair Accessibility");
            }

            boolean bikesAllowed = trips.stream()
                .anyMatch(trip -> hasValue(trip, "bikes_allowed", COMPONENT_PRESENT_VALUE::equals));
            if (bikesAllowed) {
                newComponents.add("Bikes Allowance");
            }

            long blocks = trips.stream()
                .filter(trip -> hasValue(trip, "block_id", not(String::isBlank)))
                .map(t -> t.get("block_id")).distinct().count();
            newCounts.add("Blocks: " + blocks);
            if (blocks > 0) {
                newComponents.add("Blocks");
            }

            boolean headsigns = trips.stream().anyMatch(trip -> hasValue(trip, "trip_headsign", not(String::isBlank)));
            if (headsigns) {
                newComponents.add("Headsigns");
            }

            summaryBuilder = summaryBuilder
                .addAllComponents(newComponents)
                .addAllCounts(newCounts);
        }
        return summaryBuilder;
    }

    private boolean hasValue(Map<String, String> row, String key, Predicate<String> value) {
        return row.containsKey(key) && value.test(row.get(key));
    }

    private ImmutableGtfsInputSummary.Builder processTransfers(Entry entry, Task task, InputStream inputStream, ImmutableGtfsInputSummary.Builder summaryBuilder) {
        List<Map<String, String>> transfers = processCsv(entry, task, inputStream, transfersCsv);
        if (transfers.size() > 1) {
            summaryBuilder = summaryBuilder.addComponents("Transfers");
        }
        return summaryBuilder;
    }

    private ImmutableGtfsInputSummary.Builder processTranslations(Entry entry, Task task, InputStream inputStream, ImmutableGtfsInputSummary.Builder summaryBuilder) {
        List<Map<String, String>> translations = processCsv(entry, task, inputStream, translationsCsv);
        if (translations.size() > 1) {
            summaryBuilder = summaryBuilder.addComponents("Translations");
        }
        return summaryBuilder;
    }

    ImmutableGtfsInputSummary.Builder getEmptyGtfsSummaryObject() {
        return ImmutableGtfsInputSummary.builder();
    }

    private List<Map<String, String>> processCsv(Entry entry, Task task, InputStream inputStream, CsvStructure csvStructure) {
        try {
            return Streams.collect(createOutputFormat().parse(new InputStreamReader(inputStream)), row -> asMap(csvStructure.columns(), row));
        } catch (IOException e) {
            logger.error("Failed to process {} for entry {} task {}", csvStructure.fileName(), entry.publicId(), task.name(), e);
        }
        return List.of();
    }

    private CSVFormat createOutputFormat() {
        return CSVFormat.DEFAULT.builder().setAllowMissingColumnNames(true).setHeader().build();
    }
    private void persistTaskSummaryItem(Task task, String itemName, RendererType rendererType, List<Map<String, String>> data) {
        try {
            summaryRepository.create(ImmutableSummary.of(task.id(), itemName, rendererType, objectMapper.writeValueAsBytes(data)));
        }
        catch (JsonProcessingException e) {
            logger.error("Failed to persist {}'s summary data {} generated for task {}", itemName, data, task.name(), e);
        }
    }

    private <T> void persistTaskSummaryItem(Task task, String itemName, RendererType rendererType, T data) {
        try {
            summaryRepository.create(ImmutableSummary.of(task.id(), itemName, rendererType, objectMapper.writeValueAsBytes(data)));
        }
        catch (JsonProcessingException e) {
            logger.error("Failed to persist {}'s summary data {} generated for task {}", itemName, data, task.name(), e);
        }
    }

}
