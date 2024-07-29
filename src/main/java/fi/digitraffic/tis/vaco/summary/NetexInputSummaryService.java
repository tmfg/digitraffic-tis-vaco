package fi.digitraffic.tis.vaco.summary;

import fi.digitraffic.tis.vaco.summary.model.RendererType;
import fi.digitraffic.tis.vaco.summary.model.netex.ImmutableLine;
import fi.digitraffic.tis.vaco.summary.model.netex.ImmutableNetexInputSummary;
import fi.digitraffic.tis.vaco.summary.model.netex.ImmutableRoute;
import fi.digitraffic.tis.vaco.summary.model.netex.Line;
import fi.digitraffic.tis.vaco.summary.model.netex.NetexInputSummary;
import fi.digitraffic.tis.vaco.summary.model.netex.Route;
import fi.digitraffic.tis.vaco.ui.model.summary.Card;
import fi.digitraffic.tis.vaco.ui.model.summary.ImmutableCard;
import fi.digitraffic.tis.vaco.ui.model.summary.ImmutableLabelValuePair;
import fi.digitraffic.tis.vaco.ui.model.summary.LabelValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class NetexInputSummaryService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SummaryRepository summaryRepository;

    public NetexInputSummaryService(SummaryRepository summaryRepository) {
        this.summaryRepository = summaryRepository;
    }

    public void generateNetexInputSummaries(Path downloadedPackagePath, Long taskId) throws IOException {
        logger.info("Starting NeTEx input summary generation for task {}", taskId);
        ImmutableNetexInputSummary.Builder netexInputSummaryBuilder = getEmptyNetexSummaryBuilder();
        int routeTotalCount = 0;
        int lineTotalCount = 0;
        int stopPlaceTotalCount = 0;
        int quayTotalCount = 0;
        int journeyPatternTotalCount = 0;
        int serviceJourneysTotalCount = 0;
        HashSet<String> operators = new HashSet<>();
        int operatorTotalCount = 0;

        try (ZipFile zipFile = new ZipFile(downloadedPackagePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()) {
                    netexInputSummaryBuilder.addFiles(zipEntry.getName());
                    if (!zipEntry.getName().endsWith(".xml")) {
                        continue;
                    }
                    try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
                        XMLEventReader reader = xmlInputFactory.createXMLEventReader(inputStream);
                        List<Line> lines = new ArrayList<>();
                        List<Route> routes = new ArrayList<>();

                        while (reader.hasNext()) {
                                XMLEvent nextEvent = reader.nextEvent();
                                if (nextEvent.isStartElement()) {
                                    StartElement startElement = nextEvent.asStartElement();
                                    switch (startElement.getName().getLocalPart()) {
                                        case "Operator" -> {
                                            operatorTotalCount += processOperator(reader, startElement, netexInputSummaryBuilder, zipFile.getName(), taskId, operators);
                                        }
                                        case "Route" -> {
                                            routeTotalCount++;
                                            Route route = processRoute(reader, startElement, zipFile.getName(), taskId);
                                            if (route.lineRef() != null) {
                                                routes.add(route);
                                            }
                                        }
                                        case "Line" -> {
                                            lineTotalCount++;
                                            lines.add(processLine(reader, startElement, zipFile.getName(), taskId));
                                        }
                                        case "StopPlace" -> stopPlaceTotalCount++;
                                        case "Quay" -> quayTotalCount++;
                                        case "JourneyPattern" -> journeyPatternTotalCount++;
                                        case "ServiceJourney" -> serviceJourneysTotalCount++;
                                    }
                                }
                        }
                        produceLineSummaries(netexInputSummaryBuilder, lines, routes);
                    } catch (Exception e) {
                        logger.error("Failed to process summaries from {} as part of task {}",
                            zipEntry.getName(), taskId, e);
                    }
                }
            }
        }

        List<String> counts = List.of(
            "Operators: " + operatorTotalCount,
            "Lines: " + lineTotalCount,
            "Routes: " + routeTotalCount,
            "Stop places: " + stopPlaceTotalCount,
            "Quays: " + quayTotalCount,
            "Journey patterns: " + journeyPatternTotalCount,
            "Service journeys: " + serviceJourneysTotalCount);

        netexInputSummaryBuilder.counts(counts);
        NetexInputSummary netexInputSummary = netexInputSummaryBuilder.build();
        summaryRepository.persistTaskSummaryItem(taskId, "operators", RendererType.CARD, netexInputSummary.operators());
        summaryRepository.persistTaskSummaryItem(taskId, "lines", RendererType.CARD, netexInputSummary.lines());
        summaryRepository.persistTaskSummaryItem(taskId, "files", RendererType.LIST, netexInputSummary.files());
        summaryRepository.persistTaskSummaryItem(taskId, "counts", RendererType.LIST, netexInputSummary.counts());
    }

    int processOperator(XMLEventReader reader,
                           StartElement operatorStartElement,
                           ImmutableNetexInputSummary.Builder netexInputSummaryBuilder,
                           String fileName,
                           Long taskId,
                           HashSet<String> operators) {
        ImmutableCard.Builder cardBuilder = ImmutableCard.builder();
        List<LabelValuePair> cardContent = new ArrayList<>();

        Attribute id = operatorStartElement.getAttributeByName(new QName("id"));
        if (id != null) {
            cardContent.add(ImmutableLabelValuePair.of("id", id.getValue()));
            if (operators.contains(id.getValue())) {
                return 0;
            }
        }

        while (reader.hasNext()) {
            try {
                XMLEvent nextEvent = reader.nextEvent();
                if (nextEvent.isStartElement()) {
                    StartElement startElement = nextEvent.asStartElement();
                    String localPart = startElement.getName().getLocalPart();
                    nextEvent = reader.nextEvent();
                    switch (localPart) {
                        case "Name" -> collectCharacters("name", nextEvent, (k, s) -> cardBuilder.title(s));
                        case "Email" -> collectCharacters("email", nextEvent, (k, s) -> cardContent.add(ImmutableLabelValuePair.of(k, s)));
                        case "Url" -> collectCharacters("website", nextEvent, (k, s) -> cardContent.add(ImmutableLabelValuePair.of(k, s)));
                        case "Phone" -> collectCharacters("phone", nextEvent, (k, s) -> cardContent.add(ImmutableLabelValuePair.of(k, s)));
                    }
                }
                if (nextEvent.isEndElement()) {
                    EndElement endElement = nextEvent.asEndElement();
                    if (endElement.getName().getLocalPart().equals("Operator")) {
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to process operator in {} as part of task {}", fileName, taskId, e);
            }
        }
        Card operator = cardBuilder.content(cardContent).build();
        netexInputSummaryBuilder.addOperators(operator);
        operators.add(Objects.requireNonNull(id).getValue());

        return 1;
    }
    private void collectCharacters(String key, XMLEvent event, BiConsumer<String, String> content) {
        if (event.isCharacters()) {
            String value = event.asCharacters().getData();
            content.accept(key, value);
        }
    }

    Route processRoute(XMLEventReader reader,
                       StartElement routeStartElement,
                       String fileName,
                       Long taskId) throws Exception {
        ImmutableRoute.Builder routeBuilder = ImmutableRoute.builder();
        Attribute id = routeStartElement.getAttributeByName(new QName("id"));
        if (id != null) {
            routeBuilder.id(id.getValue());
        }

        while (reader.hasNext()) {
            try {
                XMLEvent nextEvent = reader.nextEvent();
                if (nextEvent.isStartElement()) {
                    StartElement startElement = nextEvent.asStartElement();
                    String localPart = startElement.getName().getLocalPart();
                    if ("LineRef".equals(localPart)) {
                        Attribute lineRef = startElement.getAttributeByName(new QName("ref"));
                        if (lineRef != null) {
                            routeBuilder.lineRef(lineRef.getValue());
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to process route in {} as part of task {}", fileName, taskId, e);
            }
        }

        return routeBuilder.build();
    }

    Line processLine(XMLEventReader reader,
                     StartElement lineStartElement, String fileName,
                     Long taskId) throws Exception {
        ImmutableLine.Builder lineBuilder = ImmutableLine.builder();
        Attribute id = lineStartElement.getAttributeByName(new QName("id"));
        if (id != null) {
            lineBuilder.id(id.getValue());
        }

        while (reader.hasNext()) {
            try{
                XMLEvent nextEvent = reader.nextEvent();
                if (nextEvent.isStartElement()) {
                    StartElement startElement = nextEvent.asStartElement();
                    String localPart = startElement.getName().getLocalPart();
                    nextEvent = reader.nextEvent();
                    switch (localPart) {
                        case "Name" -> collectCharacters("name", nextEvent, (k, s) -> lineBuilder.name(s));
                        case "TransportMode" -> collectCharacters("transportMode", nextEvent, (k, s) -> lineBuilder.transportMode(s));
                    }
                }
                if (nextEvent.isEndElement()) {
                    EndElement endElement = nextEvent.asEndElement();
                    if (endElement.getName().getLocalPart().equals("Line")) {
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to process line in {} as part of task {}", fileName, taskId, e);
            }
        }

        return lineBuilder.build();
    }

    void produceLineSummaries(ImmutableNetexInputSummary.Builder netexInputSummaryBuilder,
                              List<Line> lines, List<Route> routes) {
        List<Card> lineCards = new ArrayList<>();

        lines.forEach(line -> {
            int routeCount = routes.stream().filter(route -> route.lineRef().equals(line.id())).toList().size();
            List<LabelValuePair> cardContent = List.of(
                ImmutableLabelValuePair.of("transportMode", line.transportMode()),
                ImmutableLabelValuePair.of("routesCount", String.valueOf(routeCount)));
            lineCards.add(ImmutableCard.builder()
                .title(line.name())
                .content(cardContent)
                .build());
        });

        netexInputSummaryBuilder.addAllLines(lineCards);
    }

    ImmutableNetexInputSummary.Builder getEmptyNetexSummaryBuilder() {
        return ImmutableNetexInputSummary.builder()
            .operators(Collections.emptyList())
            .lines(Collections.emptyList())
            .files(Collections.emptyList())
            .counts(Collections.emptyList());
    }
}
