package com.patchagent.model;

import java.io.Serializable;

/**
 * Stored in the HTTP session after a successful LDAP login.
 * Credentials are kept server-side only; the browser receives
 * a random session ID cookie (managed by Spring Session).
 */
public class SessionCredentials implements Serializable {

    public static final String SESSION_KEY = "PA_CREDENTIALS";

    private final String username;
    private final String password;
    /** "nonprod" or "prod" — chosen by the user on the login screen. */
    private final String environment;

    public SessionCredentials(String username, String password, String environment) {
        this.username    = username;
        this.password    = password;
        this.environment = (environment != null && environment.equals("prod")) ? "prod" : "nonprod";
    }

    public String getUsername()    { return username; }
    public String getPassword()    { return password; }
    public String getEnvironment() { return environment; }

    @Override
    public String toString() {
        return "SessionCredentials{username='" + username + "', env='" + environment + "'}";
    }
}
