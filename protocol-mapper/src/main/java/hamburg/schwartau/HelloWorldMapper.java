package hamburg.schwartau;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/*
 * Our own example protocol mapper.
 */
public class HelloWorldMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {


    private static final Logger LOGGER = Logger.getLogger(HelloWorldMapper.class);

    /*
     * A config which keycloak uses to display a generic dialog to configure the token.
     */
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    /*
     * The ID of the token mapper. Is public, because we need this id in our data-setup project to
     * configure the protocol mapper in keycloak.
     */
    public static final String PROVIDER_ID = "oidc-hello-world-mapper";

    public static String apigeeApiKey = System.getenv("APIGEE_API_KEY");
    public static String apigeeRolesEndpoint = System.getenv("APIGEE_ROLES_ENDPOINT");

    static {

        LOGGER.info("Using roles endpoint [" + apigeeRolesEndpoint + "] with apiKey [" + apigeeApiKey + "]");

        // The builtin protocol mapper let the user define under which claim name (key)
        // the protocol mapper writes its value. To display this option in the generic dialog
        // in keycloak, execute the following method.
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        // The builtin protocol mapper let the user define for which tokens the protocol mapper
        // is executed (access token, id token, user info). To add the config options for the different types
        // to the dialog execute the following method. Note that the following method uses the interfaces
        // this token mapper implements to decide which options to add to the config. So if this token
        // mapper should never be available for some sort of options, e.g. like the id token, just don't
        // implement the corresponding interface.
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, HelloWorldMapper.class);
    }

    @Override
    public String getDisplayCategory() {
        return "Token mapper";
    }

    @Override
    public String getDisplayType() {
        return "Hello World Mapper";
    }

    @Override
    public String getHelpText() {
        return "Adds a hello world text to the claim";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void setClaim(final IDToken token,
                            final ProtocolMapperModel mappingModel,
                            final UserSessionModel userSession,
                            final KeycloakSession keycloakSession,
                            final ClientSessionContext clientSessionCtx) {
        // adds our data to the token. Uses the parameters like the claim name which were set by the user
        // when this protocol mapper was configured in keycloak. Note that the parameters which can
        // be configured in keycloak for this protocol mapper were set in the static intializer of this class.
        //
        // Sets a static "Hello world" string, but we could write a dynamic value like a group attribute here too.

        URI targetURI;
        try {
            targetURI = new URI(apigeeRolesEndpoint);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(targetURI)
                    .header("X-APIKEY",apigeeApiKey)
                    .GET()
                    .build();

            LOGGER.info("Requesting " + targetURI.toString());

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            String body = response.body();


            LOGGER.info("Received [" + body + "]");

            // API Key: lNkkPVTImEEh1FhUSzzB7IwCGKW3HMf8
            // Endpopint: https://dev.apim.xcelenergy.com/v3/identities/roles/test?email=cxttestuser2dev@mailinator.com&mock=success

            ObjectMapper mapper = new ObjectMapper();
            UserRoles userRoles = mapper.readValue(body, UserRoles.class); //John
            LOGGER.info("Deserialized [" + userRoles + "]");

            ArrayNode array = mapper.createArrayNode();

            for (int i = 0; i < userRoles.getRoles().size(); i++) {
                UserRole roleObj = userRoles.getRoles().get(i);
    
                if (roleObj.getDataAssetKey().equals("BILLING_ID")) {
                    BillingAccountGrants bag = new BillingAccountGrants();
                    bag.setBillingId(roleObj.getDataAssetValue());
                    bag.setAssignedGrants(new Grant(){ { setGrant(roleObj.getRole()); } });
                    array.add(mapper.valueToTree(bag));
                }
            }

            LOGGER.info("Adding [" + array + "] to claim");
            OIDCAttributeMapperHelper.mapClaim(token, mappingModel, array);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            LOGGER.error("error [" + e.getMessage() + "]", e);
        }
    }
}

class BillingAccountGrants {
    private String billingId;
    private Grant assignedGrants;

    public String getBillingId(){
        return billingId;
    }

    public void setBillingId(String billingId){
        this.billingId = billingId;
    }

    public Grant getAssignedGrants(){
        return assignedGrants;
    }

    public void setAssignedGrants(Grant assignedGrants){
        this.assignedGrants = assignedGrants;
    }
}

class Grant {
    private String grant;

    public String getGrant(){
        return grant;
    }

    public void setGrant(String grant){
        this.grant = grant;
    }
}

class UserRoles {
    private List<UserRole> roles = new ArrayList<UserRole>();

    public List<UserRole> getRoles(){
        return roles;
    }

    public void setRoles(List<UserRole> roles){
        this.roles = roles;
    }
}

@JsonIgnoreProperties
class UserRole {
    private String dataAssetKey;
    private String dataAssetValue;
    private String role;
    private String expirationDate;

    public String getDataAssetKey(){
        return dataAssetKey;
    }

    public void setDataAssetKey(String key){
        this.dataAssetKey = key;
    }

    public String getDataAssetValue(){
        return dataAssetValue;
    }

    public void setDataAssetValue(String key){
        this.dataAssetValue = key;
    }

    public String getRole(){
        return role;
    }

    public void setRole(String role){
        this.role = role;
    }

    public String getExpirationDate(){
        return expirationDate;
    }

    public void setExpirationDate(String date){
        this.expirationDate = date;
    }
}
