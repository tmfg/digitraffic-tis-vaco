package fi.digitraffic.tis.vaco.exports.utils;

import jakarta.xml.bind.JAXBElement;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.Organisation_VersionStructure;
import org.rutebanken.netex.model.OrganisationsInFrame_RelStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.VersionOfObjectRefStructure;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Adaptation of Entur's <a href="https://github.com/entur/uttu/blob/master/src/main/java/no/entur/uttu/export/netex/producer/NetexObjectFactory.java"><code>NetexObjectFactory</code></code></a>
 * for our needs until aforementioned class is published.
 *
 * @see <a href="https://github.com/entur/uttu/issues/348">entur/uttu @ GitHub issue #348: consider moving NetexObjectFactory to entur/netex-java-model</a>
 * @see <a href="https://github.com/entur/uttu/blob/e278d4af33383bdf76a5728663bfe482586d06a9/src/main/java/no/entur/uttu/export/netex/producer/NetexObjectFactory.java">no.entur.uttu.export.netex.producer.NetexObjectFactory</a>
 * @see <a href="https://github.com/entur/uttu/blob/e278d4af33383bdf76a5728663bfe482586d06a9/src/main/java/no/entur/uttu/export/netex/producer/NetexIdProducer.java">no.entur.uttu.export.netex.producer.NetexIdProducer</a>
 */
@Component
public class NetexObjectFactory {

    public static final String VERSION_ONE = "1";

    private final ObjectFactory objectFactory = new ObjectFactory();

    public <E> JAXBElement<E> wrapAsJAXBElement(E entity) {
        if (entity == null) {
            return null;
        }
        return new JAXBElement(
            new QName("http://www.netex.org.uk/netex", getEntityName(entity)),
            entity.getClass(),
            null,
            entity
        );
    }

    public static <E> String getEntityName(E entity) {
        String localPart = entity.getClass().getSimpleName();

        if (entity instanceof VersionOfObjectRefStructure) {
            // Assuming all VersionOfObjectRefStructure subclasses is named as correct element + suffix ("RefStructure""))
            if (localPart.endsWith("Structure")) {
                localPart = localPart.substring(0, localPart.lastIndexOf("Structure"));
            }
        }
        return localPart;
    }

    public JAXBElement<PublicationDeliveryStructure> createPublicationDelivery(
        String netexVersion,
        String participantRef,
        ResourceFrame resourceFrame,
        LocalDateTime publicationTimestamp) {
        PublicationDeliveryStructure.DataObjects dataObjects =
            objectFactory.createPublicationDeliveryStructureDataObjects();

        dataObjects.getCompositeFrameOrCommonFrame().add(objectFactory.createResourceFrame(resourceFrame));

        PublicationDeliveryStructure publicationDeliveryStructure = objectFactory
            .createPublicationDeliveryStructure()
            .withVersion(netexVersion)
            .withPublicationTimestamp(publicationTimestamp)
            .withParticipantRef(toNMTOKENString(participantRef))
            .withDataObjects(dataObjects);
        return objectFactory.createPublicationDelivery(publicationDeliveryStructure);
    }

    private String toNMTOKENString(String org) {
        if (org == null) {
            return "unknown";
        }
        return org.replace(' ', '_').replace("(", "_").replace(")", "_");
    }

    public ResourceFrame createResourceFrame(
        String resourceFrameId,
        Collection<Authority> authorities,
        Collection<Operator> operators
    ) {
        OrganisationsInFrame_RelStructure organisationsStruct = objectFactory
            .createOrganisationsInFrame_RelStructure()
            .withOrganisation_(
                Stream
                    .concat(
                        authorities.stream().map(Organisation_VersionStructure.class::cast),
                        operators.stream().map(Organisation_VersionStructure.class::cast)
                    )
                    .distinct()
                    .collect(toDistinctOrganisation())
                    .values()
                    .stream()
                    .map(this::wrapAsJAXBElement)
                    .collect(Collectors.toList())
            );

        return objectFactory
            .createResourceFrame()
            .withOrganisations(organisationsStruct)
            .withVersion(VERSION_ONE)
            .withId(resourceFrameId);
    }

    private static Collector<Organisation_VersionStructure, ?, Map<String, Organisation_VersionStructure>> toDistinctOrganisation() {
        return Collectors.toMap(
            Organisation_VersionStructure::getId,
            Function.identity(),
            (o1, o2) -> o1
        );
    }

}
