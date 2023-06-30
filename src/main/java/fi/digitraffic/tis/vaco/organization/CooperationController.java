package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.organization.dto.ImmutableCooperationRequest;
import fi.digitraffic.tis.vaco.organization.service.CooperationService;
import jakarta.validation.Valid;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/cooperation")
@Validated
public class CooperationController {

    private final CooperationService cooperationService;

    public CooperationController(CooperationService cooperationService) {
        this.cooperationService = cooperationService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<ImmutableCooperationRequest> createCooperation(@Valid @RequestBody ImmutableCooperationRequest cooperationCommand) {
        try {
            Optional<ImmutableCooperationRequest> cooperation = cooperationService.create(cooperationCommand);
            return cooperation
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "A cooperation between given business ID-s already exists"));
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Either of the provided organizations' business ID does not exist", ex);
            // TODO: Temporarily leaving this exception handling;
            // This needs to shift some special class creation that would hold several possible "cases" of outcome;
            // (organizations not found or cooperation already exists)
        }

    }
}
