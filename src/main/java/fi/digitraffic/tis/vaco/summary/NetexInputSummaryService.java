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
import javax.xml.stream.XMLStreamException;
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
import java.util.List;
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
        int operatorTotalCount = 0;
        int routeTotalCount = 0;
        int lineTotalCount = 0;
        int stopPlaceTotalCount = 0;
        int quayTotalCount = 0;
        int journeyPatternTotalCount = 0;
        int serviceJourneysTotalCount = 0;

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
                            try {
                                XMLEvent nextEvent = reader.nextEvent();
                                if (nextEvent.isStartElement()) {
                                    StartElement startElement = nextEvent.asStartElement();
                                    switch (startElement.getName().getLocalPart()) {
                                        case "Operator" -> {
                                            operatorTotalCount++;
                                            processOperator(reader, startElement,
                                                netexInputSummaryBuilder, zipEntry.getName(), taskId);
                                        }
                                        case "Route" -> {
                                            routeTotalCount++;
                                            Route route = processRoute(reader, startElement, zipEntry.getName(), taskId);
                                            if (route.lineRef() != null) {
                                                routes.add(route);
                                            }
                                        }
                                        case "Line" -> {
                                            lineTotalCount++;
                                            lines.add(processLine(reader, startElement, zipEntry.getName(), taskId));
                                        }
                                        case "StopPlace" -> stopPlaceTotalCount++;
                                        case "Quay" -> quayTotalCount++;
                                        case "JourneyPattern" -> journeyPatternTotalCount++;
                                        case "ServiceJourney" -> serviceJourneysTotalCount++;
                                    }
                                }
                            } catch (Exception e) {
                                logger.error("Failure while processing file {} for task {}", zipEntry.getName(), taskId, e);
                            }
                        }
                        produceLineSummaries(netexInputSummaryBuilder, lines, routes);
                    } catch (XMLStreamException e) {
                        logger.error("Failure to initiate xmlInputFactory while processing file {} for task {}",
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

    void processOperator(XMLEventReader reader,
                         StartElement operatorStartElement,
                         ImmutableNetexInputSummary.Builder netexInputSummaryBuilder,
                         String fileName,
                         Long taskId) {
        ImmutableCard.Builder cardBuilder = ImmutableCard.builder();
        List<LabelValuePair> cardContent = new ArrayList<>();

        Attribute id = operatorStartElement.getAttributeByName(new QName("id"));
        if (id != null) {
            cardContent.add(ImmutableLabelValuePair.of("id", id.getValue()));
        }

        while (reader.hasNext()) {
            try {
                XMLEvent nextEvent = reader.nextEvent();
                if (nextEvent.isStartElement()) {
                    StartElement startElement = nextEvent.asStartElement();
                    switch (startElement.getName().getLocalPart()) {
                        case "Name" -> {
                            nextEvent = reader.nextEvent();
                            String name = nextEvent.asCharacters().getData();
                            cardBuilder.title(name);
                        }
                        case "Email" -> {
                            nextEvent = reader.nextEvent();
                            String email = nextEvent.asCharacters().getData();
                            cardContent.add(ImmutableLabelValuePair.of("email", email));
                        }
                        case "Url" -> {
                            nextEvent = reader.nextEvent();
                            String url = nextEvent.asCharacters().getData();
                            cardContent.add(ImmutableLabelValuePair.of("website", url));
                        }
                        case "Phone" -> {
                            nextEvent = reader.nextEvent();
                            String phone = nextEvent.asCharacters().getData();
                            cardContent.add(ImmutableLabelValuePair.of("phone", phone));
                        }
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
    }

    Route processRoute(XMLEventReader reader,
                       StartElement routeStartElement,
                       String fileName,
                       Long taskId) {
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
                    if ("LineRef".equals(startElement.getName().getLocalPart())) {
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
                     StartElement lineStartElement,
                      String fileName,
                      Long taskId) {
        ImmutableLine.Builder lineBuilder = ImmutableLine.builder();
        Attribute id = lineStartElement.getAttributeByName(new QName("id"));
        if (id != null) {
            lineBuilder.id(id.getValue());
        }

        while (reader.hasNext()) {
            try {
                XMLEvent nextEvent = reader.nextEvent();
                if (nextEvent.isStartElement()) {
                    StartElement startElement = nextEvent.asStartElement();
                    switch (startElement.getName().getLocalPart()) {
                        case "Name" -> {
                            nextEvent = reader.nextEvent();
                            lineBuilder.name(nextEvent.asCharacters().getData());
                        }
                        case "TransportMode" -> {
                            nextEvent = reader.nextEvent();
                            lineBuilder.transportMode(nextEvent.asCharacters().getData());
                        }
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