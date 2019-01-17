package controllers;

import javax.inject.Inject;
import static play.mvc.Controller.request;
import play.mvc.Http.Context;
import play.mvc.Result;
import play.mvc.Security;

public class Secured extends Security.Authenticator {

    @Inject 
    private Application controller;
    
    @Override
    public String getUsername(Context ctx) {
        return ctx.session().get(Const.LOGGED);
    }

    @Override
    public Result onUnauthorized(Context ctx) {
        return controller.login(request(), request().uri());
    }
}
