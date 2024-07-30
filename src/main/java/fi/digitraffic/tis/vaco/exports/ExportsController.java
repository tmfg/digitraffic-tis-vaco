package fi.digitraffic.tis.vaco.exports;

import jakarta.xml.bind.JAXBElement;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exports")
@PreAuthorize("hasAuthority('vaco.apiuser')")
public class ExportsController {

    private final ExportsService exportsService;

    public ExportsController(ExportsService exportsService) {
        this.exportsService = exportsService;
    }

    @GetMapping(path = "/netex/organisations", produces = MediaType.APPLICATION_XML_VALUE)
    public JAXBElement<PublicationDeliveryStructure> netexOrganisations() {
        return exportsService.netexOrganisations();
    }

}
