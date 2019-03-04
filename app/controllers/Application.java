package controllers;

import integrations.AuthInfo;
import integrations.Force;

import java.util.Date;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import views.html.*;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import models.Account;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.Security;

@Security.Authenticated(Secured.class)
public class Application extends Controller {

    @Inject
    private Force force;

    @Inject
    private FormFactory formFactory;

    // callback/redirect URL (this application's URL)
    private String oauthCallbackUrl(Http.Request request) {
        return (request.secure() ? "https://" : "http://") + request.host();
    }

    // application entry point/root address
    public Result index(String code) {
        return ok(index.render());
    }

    protected Result login(Http.Request request, String login_redirect) {
        if (request.getQueryString("code") == null) {               // if first access
            flash("login_redirect", login_redirect);                // save initially requested URL
                                                                    // request auth code
            final String url = "https://test.salesforce.com/services/oauth2/authorize?response_type=code"
                    + "&client_id=" + force.consumerKey()
                    + "&redirect_uri=" + oauthCallbackUrl(request());
            return redirect(url);                                   // re-access the application with the auth code
        } else {
            try                                                     // code received
            {
                AuthInfo token = force.getToken(request.getQueryString("code"),
                        oauthCallbackUrl(request())).toCompletableFuture().get();
                session().put(Const.TOKEN, token.accessToken);      // store token, callback URL (this application)
                session().put(Const.URL, token.instanceUrl);        // and login timestamp to the session (can be useful
                session().put(Const.LOGGED, new Date().toString()); // to implement a session timeout)

                return redirect(flash().get("login_redirect"));     // redirect to the initially requested URL
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(Application.class.getName()).log(Level.SEVERE, null, ex);
                return unauthorized("token error");
            }
        }
    }

    public Result logout() {
        if (session(Const.LOGGED) == null) {                         // no user was logged in
            return notFound("Error 404: not found");
        } else {
            String logout = session(Const.URL) + "/secur/logout.jsp";// salesforce logout as seen on the website
            force.revokeToken(getToken());
            session().clear();
            return redirect(logout);
        }
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
            if (response.getStatus() == 204) {                      // if successful, show the account
                return redirect(controllers.routes.Application.account(account.Id));
            } else {
                return badRequest(response.getBody());
            }
        });
    }

    // retrieve token and redirect URL from session
    private AuthInfo getToken() {
        return new AuthInfo(session(Const.TOKEN), session(Const.URL));
    }
}
