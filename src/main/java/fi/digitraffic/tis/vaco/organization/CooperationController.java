package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.organization.dto.ImmutableCooperationCommand;
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
    public ResponseEntity<ImmutableCooperationCommand> createCooperation(@Valid @RequestBody ImmutableCooperationCommand cooperationCommand) {
        // TODO: Here is an example of "Command" suffix not working well
        // If it was something like "Dto", same call can be used both in request and response,
        // while being an "external representation" of some model entity.
        // Or we can opt out of returning anything at the time of creation
        // but that doesn't guarantee this issue won't ever come up
        try {
            Optional<ImmutableCooperationCommand> cooperation = cooperationService.create(cooperationCommand);
            return cooperation
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "A cooperation between given business ID-s already exists"));
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Either of the provided organizations' business ID does not exist", ex);
            // Temporarily leaving this exception handling;
            // This needs to shift some special class creation that would hold several possible "cases" of outcome;
            // (organizations not found or cooperation already exists)
        }

    }
}
