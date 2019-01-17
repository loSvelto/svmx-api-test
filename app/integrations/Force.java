package integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Singleton;
import models.Account;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.mvc.Http;

@Singleton
public class Force {

    @Inject
    WSClient ws;

    @Inject
    Config config;

    public String consumerKey() {
        return config.getString("consumer.key");
    }

    String consumerSecret() {
        return config.getString("consumer.secret");
    }

    public CompletionStage<AuthInfo> getToken(String code, String redirectUrl) {
        final CompletionStage<WSResponse> responsePromise = ws.url("https://test.salesforce.com/services/oauth2/token")
                .addQueryParameter("grant_type", "authorization_code")
                .addQueryParameter("code", code)
                .addQueryParameter("client_id", consumerKey())
                .addQueryParameter("client_secret", consumerSecret())
                .addQueryParameter("redirect_uri", redirectUrl)
                .execute(Http.HttpVerbs.POST);

        return responsePromise.thenCompose(response -> {
            final JsonNode jsonNode = response.asJson();
            return CompletableFuture.completedFuture(Json.fromJson(jsonNode, AuthInfo.class));
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryResultAccount {

        public List<Account> records;
    }

    private static class AuthException extends Exception {

        AuthException(String message) {
            super(message);
        }
    }

    public CompletionStage<List<Account>> getAccounts(AuthInfo authInfo, String country, String id) {
        CompletionStage<WSResponse> responsePromise = ws.url(authInfo.instanceUrl + "/services/data/v44.0/query/")
                .addHeader("Authorization", "Bearer " + authInfo.accessToken)
                .addQueryParameter("q", "SELECT Id, Name, Type, Industry, BillingCountry FROM Account"
                        + ((country == null) || (country.equals("")) ? "" : " where BillingCountry='" + country + "'")
                        + (id == null ? "" : " where Id='" + id + "'"))
                .get();

        return responsePromise.thenCompose(response -> {
            final JsonNode jsonNode = response.asJson();
            if (jsonNode.has("error")) {
                CompletableFuture<List<Account>> completableFuture = new CompletableFuture<>();
                completableFuture.completeExceptionally(new AuthException(jsonNode.get("error").textValue()));
                return completableFuture;
            } else {
                QueryResultAccount queryResultAccount = Json.fromJson(jsonNode, QueryResultAccount.class);
                return CompletableFuture.completedFuture(queryResultAccount.records);
            }
        });
    }

    public CompletionStage<WSResponse> updateAccount(AuthInfo authInfo, Account account) {

        JsonNode accountJson = Json.toJson(account);
        // remove id field from JSON to allow update of the same s-object
        ((ObjectNode) accountJson).remove(Arrays.asList("Id", "id"));
        return ws.url(authInfo.instanceUrl + "/services/data/v44.0/"
                + "sobjects/Account/" + account.Id + "?_HttpMethod=PATCH")
                .addHeader("Authorization", "Bearer " + authInfo.accessToken)
                .post(accountJson);
    }

    public void revokeToken(AuthInfo authInfo) {
        ws.url(authInfo.instanceUrl + "/services/oauth2/revoke")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addQueryParameter("token", authInfo.accessToken).
                execute(Http.HttpVerbs.POST);
    }
}
