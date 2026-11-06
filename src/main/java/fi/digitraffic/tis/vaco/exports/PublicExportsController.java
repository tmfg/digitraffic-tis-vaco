package fi.digitraffic.tis.vaco.exports;

import jakarta.xml.bind.JAXBElement;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exports which are available without authentication. Everything under {@code /public} is permitted in
 * {@link fi.digitraffic.tis.vaco.AadOAuth2LoginSecurityConfig}, so only data that is safe to publish may be added here.
 * <p>
 * RAE (Uttu) loads its organisations from {@code /public/netex/organisations} at startup, so the path is a contract.
 * Do not move or protect it before RAE has been changed.
 */
@RestController
@RequestMapping("/public")
public class PublicExportsController {

    private final ExportsService exportsService;

    public PublicExportsController(ExportsService exportsService) {
        this.exportsService = exportsService;
    }

    @GetMapping(path = "/netex/organisations", produces = MediaType.APPLICATION_XML_VALUE)
    public JAXBElement<PublicationDeliveryStructure> netexOrganisations() {
        return exportsService.publicNetexOrganisations();
    }
}
