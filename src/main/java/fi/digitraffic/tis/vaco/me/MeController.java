package fi.digitraffic.tis.vaco.me;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.me.model.ImmutableMe;
import fi.digitraffic.tis.vaco.me.model.Me;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * "me" endpoints allow the user to request data relating to themselves, e.g. which companies they have access to.
 */
@RestController
@RequestMapping("/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = Objects.requireNonNull(meService);
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Me>> fetch() {
        Set<Company> companies = meService.findCompanies();

        return ResponseEntity.ok(
            new Resource<>(
                ImmutableMe.of(companies),
                null,
                Map.of()));
    }

}
