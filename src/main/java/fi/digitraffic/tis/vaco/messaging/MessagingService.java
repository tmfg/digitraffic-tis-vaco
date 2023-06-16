package fi.digitraffic.tis.vaco.messaging;

import fi.digitraffic.tis.vaco.conversion.model.ConversionJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.queuehandler.model.ProcessingState;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;

public interface MessagingService {
    void submitProcessingJob(DelegationJobMessage delegationJobMessage);

    void submitValidationJob(ValidationJobMessage jobDescription);

    void submitConversionJob(ConversionJobMessage jobDescription);

    void updateJobProcessingStatus(ImmutableDelegationJobMessage jobDescription, ProcessingState start);
}
