package controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import java.util.Arrays;
import java.util.Date;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import views.html.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import models.Account;
import play.data.Form;
import play.data.FormFactory;

public class Application extends Controller {

    @Inject
    private Force force;

    @Inject
    private FormFactory formFactory;

    // Session cookies string constants
    private static final String TOKEN = "token";
    private static final String URL = "callbackURL";
    private static final String LOGGED = "logged";

    // callback/redirect URL (this application's URL)
    private String oauthCallbackUrl(Http.Request request) {
        return (request.secure() ? "https" : "http") + "://" + request.host();
    }

    // application entry point. Directs to sandbox login page if not logged in
    public Result index(String code) {
        if (session(LOGGED) == null) {  // new session
            if (code == null) {
                // request auth code
                final String url = "https://test.salesforce.com/services/oauth2/authorize?response_type=code"
                        + "&client_id=" + force.consumerKey()
                        + "&redirect_uri=" + oauthCallbackUrl(request());
                return redirect(url);
            } else {
                try // code received
                {
                    // get OAuth token and store to session
                    Force.AuthInfo token = force.getToken(code, oauthCallbackUrl(request()))
                            .toCompletableFuture().get();
                    session().put(TOKEN, token.accessToken);
                    session().put(URL, token.instanceUrl);
                    session().put(LOGGED, new Date().toString());
                    return ok(index.render());
                } catch (InterruptedException | ExecutionException ex) {
                    Logger.getLogger(Application.class.getName()).log(Level.SEVERE, null, ex);
                    return unauthorized("token error");
                }
            }
        } else { // already logged in
            return ok(index.render());
        }
    }

    public Result logout() {
        String logout = session(URL) + "/secur/logout.jsp"; // salesforce logout
        force.revokeToken(getToken());
        session().clear();
        return redirect(logout);
    }

    public CompletionStage<Result> accounts(String country) {
        return force.getAccounts(getToken(), country, null).thenApply(accountList
                -> ok(accounts.render(accountList)));
    }

    public CompletionStage<Result> account(String id) {
        return force.getAccounts(getToken(), null, id).thenApply(accountList
                -> ok(account.render(accountList)));
    }

    public CompletionStage<Result> editAccount(String id) {
        return force.getAccounts(getToken(), null, id).thenApply(accountList
                -> ok(edit_account.render(accountList)));
    }

    public CompletionStage<Result> updateAccount() {
        // read data from html form
        Form<Account> accountForm = formFactory.form(Account.class);
        Account account = accountForm.bindFromRequest().get();

        return force.updateAccount(getToken(), account).thenApply(response -> {
            if (response.getStatus() == 204) {
                return redirect(controllers.routes.Application.account(account.Id));
            } else {
                return badRequest(response.getBody());
            }
        });
    }

    // retrieve token and redirect URL from session
    private Force.AuthInfo getToken() {
        return new Force.AuthInfo(session(TOKEN), session(URL));
    }

    @Singleton
    public static class Force {

        @Inject
        WSClient ws;

        @Inject
        Config config;

        String consumerKey() {
            return config.getString("consumer.key");
        }

        String consumerSecret() {
            return config.getString("consumer.secret");
        }

        CompletionStage<AuthInfo> getToken(String code, String redirectUrl) {
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

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class AuthInfo {

            @JsonProperty("access_token")
            public String accessToken;

            @JsonProperty("instance_url")
            public String instanceUrl;
            
            private AuthInfo(String token, String url) {
                this.accessToken = token;
                this.instanceUrl = url;
            }
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

        CompletionStage<WSResponse> updateAccount(AuthInfo authInfo, Account account) {

            JsonNode accountJson = Json.toJson(account);
            // remove id field from JSON to allow update of the same s-object
            ((ObjectNode) accountJson).remove(Arrays.asList("Id", "id"));
            return ws.url(authInfo.instanceUrl + "/services/data/v44.0/"
                    + "sobjects/Account/" + account.Id + "?_HttpMethod=PATCH")
                    .addHeader("Authorization", "Bearer " + authInfo.accessToken)
                    .post(accountJson);
        }

        void revokeToken(AuthInfo authInfo) {
            ws.url(authInfo.instanceUrl + "/services/oauth2/revoke")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addQueryParameter("token", authInfo.accessToken).
                    execute(Http.HttpVerbs.POST);
        }
    }
}
