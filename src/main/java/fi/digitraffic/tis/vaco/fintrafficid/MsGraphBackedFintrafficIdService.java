package fi.digitraffic.tis.vaco.fintrafficid;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.directoryobjects.item.getmembergroups.GetMemberGroupsPostRequestBody;
import com.microsoft.graph.directoryobjects.item.getmembergroups.GetMemberGroupsPostResponse;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.serialization.UntypedObject;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;
import fi.digitraffic.tis.vaco.fintrafficid.model.ImmutableFintrafficIdGroup;
import fi.digitraffic.tis.vaco.fintrafficid.model.ImmutableOrganizationData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MsGraphBackedFintrafficIdService implements FintrafficIdService {

    private static final String MICROSOFT_GRAPH_SCOPE = "https://graph.microsoft.com/.default";

    private final GraphServiceClient graphClient;
    private final VacoProperties vacoProperties;
    private final CachingService cachingService;

    public MsGraphBackedFintrafficIdService(VacoProperties vacoProperties, CachingService cachingService) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.graphClient = initializeMsGraphClient(vacoProperties);
        this.cachingService = Objects.requireNonNull(cachingService);
    }

    private GraphServiceClient initializeMsGraphClient(VacoProperties vacoProperties) {
        ClientSecretCredential credentials = new ClientSecretCredentialBuilder()
            .clientId(vacoProperties.msGraph().clientId())
            .tenantId(vacoProperties.msGraph().tenantId())
            .clientSecret(vacoProperties.msGraph().clientSecret())
            .build();

        return new GraphServiceClient(credentials, MICROSOFT_GRAPH_SCOPE);
    }

    @Override
    public List<FintrafficIdGroup> getGroups(String oid) {
        GetMemberGroupsPostRequestBody getMemberGroupsPostRequestBody = new GetMemberGroupsPostRequestBody();
        getMemberGroupsPostRequestBody.setSecurityEnabledOnly(false);

        GetMemberGroupsPostResponse result = graphClient.directoryObjects()
            .byDirectoryObjectId(oid)
            .getMemberGroups()
            .post(getMemberGroupsPostRequestBody);

        return Streams.map(result.getValue(), this::getGroup)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    @Override
    public Optional<FintrafficIdGroup> getGroup(String groupId) {
        return cachingService.cacheMsGraph("getGroup-" + groupId, key -> {
            Group g = graphClient.groups().byGroupId(groupId).get(requestConfiguration -> {
                requestConfiguration.queryParameters.select = new String []{"displayName", "id", "description", vacoProperties.msGraph().groupSchemaExtension()};
            });
            ImmutableFintrafficIdGroup.Builder builder = ImmutableFintrafficIdGroup.builder()
                .id(g.getId())
                .description(g.getDescription())
                .displayName(g.getDisplayName());

            if (g.getAdditionalData() != null && g.getAdditionalData().containsKey(vacoProperties.msGraph().groupSchemaExtension())) {
                // unwrap Kiota boxed values
                Map<String, Object> rawValues = new HashMap<>();
                ((UntypedObject) g.getAdditionalData().get(vacoProperties.msGraph().groupSchemaExtension())).getValue().forEach((k, v) ->
                    rawValues.put(k, v.getValue())
                );

                builder.organizationData(ImmutableOrganizationData.builder()
                    .phoneNumber((String) rawValues.get("phoneNumber"))
                    .address((String) rawValues.get("address"))
                    .contactName((String) rawValues.get("contactName"))
                    .postalCode((String) rawValues.get("postalCode"))
                    .municipality((String) rawValues.get("municipality"))
                    .businessId((String) rawValues.get("businessId"))
                    .name((String) rawValues.get("name"))
                    .contactPhoneNumber((String) rawValues.get("contactPhoneNumber"))
                    .astraGovernmentOrganization((Boolean) rawValues.get("astraGovernmentOrganization"))
                    .build());
            }

            return builder.build();
        });
    }
}
