package fi.digitraffic.tis.vaco.configuration;

import fi.digitraffic.tis.vaco.VacoException;
import jakarta.xml.bind.JAXBContext;
import static jakarta.xml.bind.JAXBContext.newInstance;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.xml.AbstractXmlHttpMessageConverter;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enables support for serializing {@link JAXBElement} or any given JAXB managed entities directly from controllers as
 * XML.
 */
public class JaxbHttpMessageConverter extends AbstractXmlHttpMessageConverter<Object> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JAXBContext publicationDeliveryContext;
    private final Set<Class<?>> supportedClasses;

    public JaxbHttpMessageConverter(Class<?>... clazzes) {
        this.publicationDeliveryContext = createContext(clazzes);
        this.supportedClasses = new HashSet<>();
        this.supportedClasses.add(JAXBElement.class);
        this.supportedClasses.addAll(Set.of(clazzes));
    }

    @Override
    protected Object readFromSource(Class<?> clazz, HttpHeaders headers, Source source) {
        return null;
    }

    @Override
    protected void writeToResult(Object o, HttpHeaders headers, Result result) throws Exception {
        createMarshaller().marshal(o, result);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return supportedClasses.contains(clazz);
    }

    @Override
    public List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
        return List.of(MediaType.APPLICATION_XML);
    }

    private Marshaller createMarshaller() throws JAXBException {
        Marshaller marshallerInstance = publicationDeliveryContext.createMarshaller();
        marshallerInstance.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshallerInstance.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
        return marshallerInstance;
    }

    private JAXBContext createContext(Class<?>... clazz) {
        try {
            JAXBContext jaxbContext = newInstance(clazz);
            logger.info("Created context {}", jaxbContext.getClass());
            return jaxbContext;
        } catch (JAXBException e) {
            throw new VacoException("Could not create instance of JAXB context for class " + clazz, e);
        }
    }
}
