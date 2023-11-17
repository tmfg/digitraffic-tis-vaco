package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record Email(@NotBlank String from,
                    List<String> replyTo) {

}
