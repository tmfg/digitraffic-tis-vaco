package fi.digitraffic.tis.vaco.healthcheck;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthcheckController {

    private final String sharedSecret;

    public HealthcheckController(@Value("${vaco.health.key}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<String> ok(
        @RequestParam(name="key", required = false) String key) {
        if (key != null) {
            if (sharedSecret.equals(key)) {
                return ResponseEntity.ok().body("ok");
            } else {
                return ResponseEntity.badRequest().body("not ok");
            }
        }
        return ResponseEntity.ok().body("ok");
    }
}
