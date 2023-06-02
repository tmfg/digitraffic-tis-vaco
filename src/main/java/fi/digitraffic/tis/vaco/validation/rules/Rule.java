package fi.digitraffic.tis.vaco.validation.rules;

import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.validation.model.Result;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;

import java.util.concurrent.CompletableFuture;

public interface Rule {
    String getIdentifyingName();

    CompletableFuture<Result<ValidationReport>> execute(ImmutablePhaseData<ImmutableFileReferences> phaseData);
}
