package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.rules.model.ImmutableTaskSummaryItem;
import fi.digitraffic.tis.vaco.rules.model.gtfs.summary.Agency;
import fi.digitraffic.tis.vaco.rules.model.gtfs.summary.FeedInfo;
import fi.digitraffic.tis.vaco.rules.model.gtfs.summary.ImmutableGtfsTaskSummary;
import fi.digitraffic.tis.vaco.rules.model.gtfs.summary.Route;
import fi.digitraffic.tis.vaco.rules.model.gtfs.summary.Shape;
import fi.digitraffic.tis.vaco.rules.model.gtfs.summary.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class GtfsTaskSummaryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String COMPONENT_PRESENT_VALUE = "1";
    private final TaskSummaryRepository taskSummaryRepository;
    private final ObjectMapper objectMapper;

    public GtfsTaskSummaryService(TaskSummaryRepository taskSummaryRepository, ObjectMapper objectMapper) {
        this.taskSummaryRepository = taskSummaryRepository;
        this.objectMapper = objectMapper;
    }

    public void generateGtfsSummaries(Path downloadedPackagePath, Long taskId) throws IOException {
        ImmutableGtfsTaskSummary gtfsTaskSummary = getEmptyGtfsSummaryObject();

        try (ZipFile zipFile = new ZipFile(downloadedPackagePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()) {
                    gtfsTaskSummary = gtfsTaskSummary
                        .withFiles(Streams.append(gtfsTaskSummary.files(), zipEntry.getName()));

                    try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                        switch (zipEntry.getName()) {
                            case "agency.txt" ->
                                gtfsTaskSummary = processAgencies(inputStream, taskId, gtfsTaskSummary);
                            case "feed_info.txt" ->
                                gtfsTaskSummary = processFeedInfo(inputStream, taskId, gtfsTaskSummary);
                            case "routes.txt" ->
                                gtfsTaskSummary = processRoutes(inputStream, taskId, gtfsTaskSummary);
                            case "shapes.txt" ->
                                gtfsTaskSummary = processShapes(inputStream, taskId, gtfsTaskSummary);
                            case "stops.txt" ->
                                gtfsTaskSummary = processStops(inputStream, taskId, gtfsTaskSummary, zipEntry.getName());
                            case "trips.txt" ->
                                gtfsTaskSummary = processTrips(inputStream, taskId, gtfsTaskSummary);
                            case "transfers.txt" ->
                                gtfsTaskSummary = processTransfers(inputStream, taskId, gtfsTaskSummary, zipEntry.getName());
                            default ->
                                logger.info("Did not process a file {} while generating summaries for task {}", zipEntry.getName(), taskId);
                        }
                    }
                }
            }
        }

        persistTaskSummaryItem(taskId, "agencies", gtfsTaskSummary.agencies());
        persistTaskSummaryItem(taskId, "feedInfo", gtfsTaskSummary.feedInfo());
        persistTaskSummaryItem(taskId, "files", gtfsTaskSummary.files());
        persistTaskSummaryItem(taskId, "counts", gtfsTaskSummary.counts());
        persistTaskSummaryItem(taskId, "components", gtfsTaskSummary.components());
    }

    <T> void persistTaskSummaryItem(Long taskId, String itemName, T data) {
        try {
            taskSummaryRepository.create(ImmutableTaskSummaryItem.of(taskId, itemName, objectMapper.writeValueAsBytes(data)));
        }
        catch (JsonProcessingException e) {
            logger.error("Failed to persist {}'s summary data {} generated for task {}", itemName, data, taskId, e);
        }
    }

    ImmutableGtfsTaskSummary getEmptyGtfsSummaryObject() {
        return ImmutableGtfsTaskSummary.builder()
            .agencies(Collections.emptyList())
            .feedInfo(new FeedInfo())
            .files(Collections.emptyList())
            .counts(Collections.emptyList())
            .components(Collections.emptyList())
            .build();
    }

    <T> List<T> getCsvBeans(InputStream inputStream, Class<T> beanClass) {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return new CsvToBeanBuilder<T>(csvReader)
                .withType(beanClass)
                .build()
                .parse();
        } catch (IOException e) {
            logger.error("Failed to parse into {}", beanClass, e);
        }
        return Collections.emptyList();
    }

    List<String[]> getCsvRows(InputStream inputStream, String fileName) {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return csvReader.readAll();
        } catch (IOException | CsvException e) {
            logger.error("Failed to parse {}", fileName, e);
        }
        return Collections.emptyList();
    }

    ImmutableGtfsTaskSummary processAgencies(InputStream inputStream, Long taskId, ImmutableGtfsTaskSummary gtfsTaskSummary) {
        try {
            List<Agency> agencies = getCsvBeans(inputStream, Agency.class);
            return gtfsTaskSummary
                .withAgencies(agencies)
                .withCounts(Streams.append(
                    gtfsTaskSummary.counts(),
                    "Agencies:  " + agencies.size()));
        } catch (Exception e) {
            logger.error("Failed to process agencies for task {}", taskId);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsTaskSummary processFeedInfo(InputStream inputStream, Long taskId, ImmutableGtfsTaskSummary gtfsTaskSummary) {
        try {
            List<FeedInfo> feedInfoList = getCsvBeans(inputStream, FeedInfo.class);
            if (!feedInfoList.isEmpty()) {
                return gtfsTaskSummary
                    .withFeedInfo(feedInfoList.get(0))
                    .withComponents(Streams.append(
                        gtfsTaskSummary.components(),
                        "Feed information"));
            }
        }
        catch (Exception e) {
            logger.error("Failed to process feed info for task {}", taskId);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsTaskSummary processRoutes(InputStream inputStream, Long taskId, ImmutableGtfsTaskSummary gtfsTaskSummary) {
        try {
            List<Route> routes = getCsvBeans(inputStream, Route.class);
            List<String> newComponents = new ArrayList<>();

            boolean colours = routes.stream()
                .anyMatch(route -> route.getRouteColor() != null && !route.getRouteColor().isBlank());
            if (colours) {
                newComponents.add("Route Colors");
            }
            boolean names = routes.stream()
                .anyMatch(route -> (route.getRouteShortName() != null && !route.getRouteShortName().isBlank())
                    || (route.getRouteLongName() != null && !route.getRouteLongName().isBlank()));
            if (names) {
                newComponents.add("Route Names");
            }

            return gtfsTaskSummary
                .withCounts(Streams.append(gtfsTaskSummary.counts(), "Routes:  " + routes.size()))
                .withComponents(Streams.concat(gtfsTaskSummary.components(), newComponents).toList());
        }
        catch (Exception e) {
            logger.error("Failed to process routes for task {}", taskId);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsTaskSummary processShapes(InputStream inputStream,
                                           Long taskId,
                                           ImmutableGtfsTaskSummary gtfsTaskSummary) {
        try {
            List<Shape> shapes = getCsvBeans(inputStream, Shape.class);
            long uniqueShapesCount = shapes.stream().map(Shape::getShapeId).distinct().count();
            gtfsTaskSummary = gtfsTaskSummary
                .withCounts(Streams.append(
                    gtfsTaskSummary.counts(),
                    "Shapes:  " + uniqueShapesCount));

            if (!shapes.isEmpty()) {
                return gtfsTaskSummary.withComponents(Streams.append(gtfsTaskSummary.components(), "Shapes"));
            }
        }
        catch (Exception e) {
            logger.error("Failed to process shapes for task {}", taskId);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsTaskSummary processStops(InputStream inputStream,
                                          Long taskId,
                                          ImmutableGtfsTaskSummary gtfsTaskSummary,
                                          String fileName) {
        try {
            List<String[]> stops = getCsvRows(inputStream, fileName);
            return gtfsTaskSummary
                .withCounts(Streams.append(gtfsTaskSummary.counts(), "Stops:  " + Math.max(stops.size() - 1, 0)));
        }
        catch (Exception e) {
            logger.error("Failed to process stops for task {}", taskId);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsTaskSummary processTrips(InputStream inputStream, Long taskId, ImmutableGtfsTaskSummary gtfsTaskSummary) {
        try {
            List<Trip> trips = getCsvBeans(inputStream, Trip.class);
            gtfsTaskSummary = gtfsTaskSummary
                .withCounts(Streams.append(gtfsTaskSummary.counts(), "Trips:  " +trips.size()));

            if (!trips.isEmpty()) {
                List<String> newComponents = new ArrayList<>();
                List<String> newCounts = new ArrayList<>();

                boolean wheelchairAccessibility = trips.stream()
                    .anyMatch(trip -> COMPONENT_PRESENT_VALUE.equals(trip.getWheelchairAccessible()));
                if (wheelchairAccessibility) {
                    newComponents.add("Wheelchair Accessibility");
                }

                boolean bikesAllowed = trips.stream()
                    .anyMatch(trip -> COMPONENT_PRESENT_VALUE.contains(trip.getBikesAllowed()));
                if (bikesAllowed) {
                    newComponents.add("Bikes Allowance");
                }

                long blocks = trips.stream().map(Trip::getBlockId).distinct().count();
                newCounts.add("Blocks:  " + blocks);
                if (blocks > 0) {
                    newComponents.add("Blocks");
                }

                boolean headsigns = trips.stream().anyMatch(trip -> trip.getTripHeadsign() != null && !trip.getTripHeadsign().isBlank());
                if (headsigns) {
                    newComponents.add("Headsigns");
                }

                return gtfsTaskSummary
                    .withComponents(Streams.concat(gtfsTaskSummary.components(), newComponents).toList())
                    .withCounts(Streams.concat(gtfsTaskSummary.counts(), newCounts).toList());
            }

            return gtfsTaskSummary;
        } catch(Exception e) {
            logger.error("Failed to process trips for task {}", taskId);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsTaskSummary processTransfers(InputStream inputStream,
                                              Long taskId,
                                              ImmutableGtfsTaskSummary gtfsTaskSummary,
                                              String fileName) {
        try {
            List<String[]> transfers = getCsvRows(inputStream, fileName);

            if (!transfers.isEmpty()) {
                return gtfsTaskSummary.withComponents(Streams.append(gtfsTaskSummary.components(), "Transfers"));
            }
        }
        catch (Exception e) {
            logger.error("Failed to process shapes for task {}", taskId);
        }

        return gtfsTaskSummary;
    }
}
