package controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import views.html.index;
import views.html.accounts;
import views.html.account;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class Application extends Controller {

    @Inject
    private Force force;

    private String oauthCallbackUrl(Http.Request request) {
        return (request.secure() ? "https" : "http") + "://" + request.host();
    }

    public Result index(String code) {
        if (code == null) {
            // start oauth
            final String url = "https://test.salesforce.com/services/oauth2/authorize?response_type=code"
                    + "&client_id=" + force.consumerKey()
                    + "&redirect_uri=" + oauthCallbackUrl(request());
            return redirect(url);
        } else {
            force.getToken(code, oauthCallbackUrl(request()));
            return ok(index.render());
        }
    }

    public CompletionStage<Result> accounts (String country) {
        return force.getAuthInfo().thenCompose(authInfo -> 
                force.getAccounts(authInfo, country, null)).thenApply( accountList ->
                ok(accounts.render(accountList)));
    }
    
    public CompletionStage<Result> account (String id) {
        return force.getAuthInfo().thenCompose(authInfo -> 
                force.getAccounts(authInfo, null, id)).thenApply( accountList ->
                ok(account.render(accountList)));
    }
    
    @Singleton
    public static class Force {

        @Inject
        WSClient ws;

        @Inject
        Config config;

        CompletableFuture<AuthInfo> token;

        String consumerKey() {
            return config.getString("consumer.key");
        }

        String consumerSecret() {
            return config.getString("consumer.secret");
        }

        CompletionStage<Void> getToken(String code, String redirectUrl) {
            final CompletionStage<WSResponse> responsePromise = ws.url("https://test.salesforce.com/services/oauth2/token")
                    .addQueryParameter("grant_type", "authorization_code")
                    .addQueryParameter("code", code)
                    .addQueryParameter("client_id", consumerKey())
                    .addQueryParameter("client_secret", consumerSecret())
                    .addQueryParameter("redirect_uri", redirectUrl)
                    .execute(Http.HttpVerbs.POST);

            responsePromise.thenCompose(response -> {
                final JsonNode jsonNode = response.asJson();

                token = CompletableFuture.completedFuture(Json.fromJson(jsonNode, AuthInfo.class));
                return token;
            }
            );
            return new CompletableFuture<>();
        }

        CompletableFuture<AuthInfo> getAuthInfo() {
            return token;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Account {

            public String Id;
            public String Name;
            public String Type;
            public String Industry;
            public String BillingCountry;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class QueryResultAccount {

            public List<Account> records;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class AuthInfo {

            @JsonProperty("access_token")
            public String accessToken;

            @JsonProperty("instance_url")
            public String instanceUrl;
        }

        public static class AuthException extends Exception {

            AuthException(String message) {
                super(message);
            }
        }

        CompletionStage<List<Account>> getAccounts(AuthInfo authInfo, String country, String id) {
            CompletionStage<WSResponse> responsePromise = ws.url(authInfo.instanceUrl + "/services/data/v44.0/query/")
                    .addHeader("Authorization", "Bearer " + authInfo.accessToken)
                    .addQueryParameter("q", "SELECT Id, Name, Type, Industry, BillingCountry FROM Account"
                    + ((country == null) || (country == "") ? "" : " where BillingCountry='" + country + "'")
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
    }

}
