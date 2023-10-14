package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.ui.model.ImmutableBootstrap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ui")
public class UiController {

    private final VacoProperties vacoProperties;

    public UiController(VacoProperties vacoProperties) {
        this.vacoProperties = vacoProperties;
    }

    @GetMapping(path = "/bootstrap")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<ImmutableBootstrap> bootstrap() {
        return ResponseEntity.ok()
            .body(ImmutableBootstrap.of(
                vacoProperties.environment(),
                vacoProperties.baseUrl(),
                vacoProperties.azureAd().tenantId(),
                vacoProperties.azureAd().clientId()));
    }
}
