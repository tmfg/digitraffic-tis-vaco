package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.InvalidInputException;
import fi.digitraffic.tis.vaco.ItemExistsException;
import fi.digitraffic.tis.vaco.organization.service.CooperationService;
import fi.digitraffic.tis.vaco.validation.dto.ImmutableCooperationCommand;
import jakarta.validation.Valid;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/cooperation")
public class CooperationController {

    private final CooperationService cooperationService;

    public CooperationController(CooperationService cooperationService) {
        this.cooperationService = cooperationService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<ImmutableCooperationCommand> createCooperation(@Valid @RequestBody ImmutableCooperationCommand cooperationDto) {
        try {
            ImmutableCooperationCommand cooperation = cooperationService.create(cooperationDto);
            return ResponseEntity.ok(cooperation);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Either of the provided organizations' business ID does not exist");
        } catch (ItemExistsException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        } catch (InvalidInputException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
