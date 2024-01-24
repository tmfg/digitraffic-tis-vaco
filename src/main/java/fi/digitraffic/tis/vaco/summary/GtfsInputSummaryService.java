package fi.digitraffic.tis.vaco.summary;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.summary.model.RendererType;
import fi.digitraffic.tis.vaco.summary.model.gtfs.Agency;
import fi.digitraffic.tis.vaco.summary.model.gtfs.FeedInfo;
import fi.digitraffic.tis.vaco.summary.model.gtfs.ImmutableGtfsInputSummary;
import fi.digitraffic.tis.vaco.summary.model.gtfs.Route;
import fi.digitraffic.tis.vaco.summary.model.gtfs.Shape;
import fi.digitraffic.tis.vaco.summary.model.gtfs.Stop;
import fi.digitraffic.tis.vaco.summary.model.gtfs.Trip;
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
public class GtfsInputSummaryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String COMPONENT_PRESENT_VALUE = "1";
    private final SummaryRepository summaryRepository;

    public GtfsInputSummaryService(SummaryRepository summaryRepository) {
        this.summaryRepository = summaryRepository;
    }

    public void generateGtfsInputSummaries(Path downloadedPackagePath, Long taskId) throws IOException {
        logger.info("Starting GTFS input summary generation for task {}", taskId);
        ImmutableGtfsInputSummary gtfsTaskSummary = getEmptyGtfsSummaryObject();

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
                                gtfsTaskSummary = processStops(inputStream, taskId, gtfsTaskSummary);
                            case "trips.txt" ->
                                gtfsTaskSummary = processTrips(inputStream, taskId, gtfsTaskSummary);
                            case "transfers.txt" ->
                                gtfsTaskSummary = processTransfers(inputStream, taskId, gtfsTaskSummary, zipEntry.getName());
                            case "translations.txt" ->
                                gtfsTaskSummary = processTranslations(inputStream, taskId, gtfsTaskSummary, zipEntry.getName());
                            default ->
                                logger.info("Did not process a file {} while generating summaries for task {}", zipEntry.getName(), taskId);
                        }
                    }
                }
            }
        }

        summaryRepository.persistTaskSummaryItem(taskId, "agencies", RendererType.CARD, gtfsTaskSummary.agencies());
        summaryRepository.persistTaskSummaryItem(taskId, "feedInfo", RendererType.TABULAR, gtfsTaskSummary.feedInfo());
        summaryRepository.persistTaskSummaryItem(taskId, "files", RendererType.LIST, gtfsTaskSummary.files());
        summaryRepository.persistTaskSummaryItem(taskId, "counts", RendererType.LIST, gtfsTaskSummary.counts());
        summaryRepository.persistTaskSummaryItem(taskId, "components", RendererType.LIST, gtfsTaskSummary.components());
    }

    ImmutableGtfsInputSummary getEmptyGtfsSummaryObject() {
        return ImmutableGtfsInputSummary.builder()
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
                .withIgnoreEmptyLine(true)
                .withIgnoreLeadingWhiteSpace(true)
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

    ImmutableGtfsInputSummary processAgencies(InputStream inputStream, Long taskId, ImmutableGtfsInputSummary gtfsTaskSummary) {
        try {
            List<Agency> agencies = getCsvBeans(inputStream, Agency.class);
            return gtfsTaskSummary
                .withAgencies(agencies)
                .withCounts(Streams.append(
                    gtfsTaskSummary.counts(),
                    "Agencies:  " + agencies.size()));
        } catch (Exception e) {
            logger.error("Failed to process agencies for task {}", taskId, e);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsInputSummary processFeedInfo(InputStream inputStream, Long taskId, ImmutableGtfsInputSummary gtfsTaskSummary) {
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
            logger.error("Failed to process feed info for task {}", taskId, e);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsInputSummary processRoutes(InputStream inputStream, Long taskId, ImmutableGtfsInputSummary gtfsTaskSummary) {
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
            logger.error("Failed to process routes for task {}", taskId, e);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsInputSummary processShapes(InputStream inputStream,
                                            Long taskId,
                                            ImmutableGtfsInputSummary gtfsTaskSummary) {
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
            logger.error("Failed to process shapes for task {}", taskId, e);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsInputSummary processStops(InputStream inputStream,
                                           Long taskId,
                                           ImmutableGtfsInputSummary gtfsTaskSummary) {
        try {
            List<Stop> stops = getCsvBeans(inputStream, Stop.class);
            List<String> components = new ArrayList<>();
            if (!stops.isEmpty() && stops.stream()
                .filter(stop -> stop.getLocationType() != null && !stop.getLocationType().isBlank())
                .map(Stop::getLocationType).distinct().findAny().isPresent()) {
                components.add("Location Types");
            }
            return gtfsTaskSummary
                .withComponents(Streams.concat(gtfsTaskSummary.components(), components).toList())
                .withCounts(Streams.append(gtfsTaskSummary.counts(), "Stops:  " + stops.size()));
        }
        catch (Exception e) {
            logger.error("Failed to process stops for task {}", taskId, e);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsInputSummary processTrips(InputStream inputStream, Long taskId, ImmutableGtfsInputSummary gtfsTaskSummary) {
        try {
            List<Trip> trips = getCsvBeans(inputStream, Trip.class);
            gtfsTaskSummary = gtfsTaskSummary
                .withCounts(Streams.append(gtfsTaskSummary.counts(), "Trips:  " +trips.size()));

            if (!trips.isEmpty()) {
                List<String> newComponents = new ArrayList<>();
                List<String> newCounts = new ArrayList<>();

                boolean wheelchairAccessibility = trips.stream()
                    .anyMatch(trip -> trip.getWheelchairAccessible() != null &&  COMPONENT_PRESENT_VALUE.equals(trip.getWheelchairAccessible()));
                if (wheelchairAccessibility) {
                    newComponents.add("Wheelchair Accessibility");
                }

                boolean bikesAllowed = trips.stream()
                    .anyMatch(trip -> trip.getBikesAllowed() != null && COMPONENT_PRESENT_VALUE.equals(trip.getBikesAllowed()));
                if (bikesAllowed) {
                    newComponents.add("Bikes Allowance");
                }

                long blocks = trips.stream()
                    .filter(trip -> trip.getBlockId() != null && !trip.getBlockId().isBlank())
                    .map(Trip::getBlockId).distinct().count();
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
            logger.error("Failed to process trips for task {}", taskId, e);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsInputSummary processTransfers(InputStream inputStream,
                                               Long taskId,
                                               ImmutableGtfsInputSummary gtfsTaskSummary,
                                               String fileName) {
        try {
            List<String[]> transfers = getCsvRows(inputStream, fileName);

            if (transfers.size() > 1) {
                return gtfsTaskSummary.withComponents(Streams.append(gtfsTaskSummary.components(), "Transfers"));
            }
        }
        catch (Exception e) {
            logger.error("Failed to process transfers for task {}", taskId, e);
        }

        return gtfsTaskSummary;
    }

    ImmutableGtfsInputSummary processTranslations(InputStream inputStream,
                                                  Long taskId,
                                                  ImmutableGtfsInputSummary gtfsTaskSummary,
                                                  String fileName) {
        try {
            List<String[]> translations = getCsvRows(inputStream, fileName);

            if (translations.size() > 1) {
                return gtfsTaskSummary.withComponents(Streams.append(gtfsTaskSummary.components(), "Translations"));
            }
        }
        catch (Exception e) {
            logger.error("Failed to process translations for task {}", taskId, e);
        }

        return gtfsTaskSummary;
    }
}
