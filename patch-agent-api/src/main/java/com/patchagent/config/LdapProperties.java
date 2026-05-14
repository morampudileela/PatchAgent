package com.patchagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code ldap.*} block in application.yml.
 *
 * <pre>
 * ldap:
 *   environment: nonprod
 *   nonprod:
 *     server: ldap://...
 *     domain: NONPROD
 *     base-dn: DC=nonprod,DC=company,DC=com
 *     required-group: CN=patch-admins,...
 *     recursive-group: true
 *   prod:
 *     ...
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "ldap")
public class LdapProperties {

    /** Which environment block to use: "nonprod" or "prod". */
    private String environment = "nonprod";

    private Env nonprod = new Env();
    private Env prod     = new Env();

    // ── Accessors ────────────────────────────────────────────────────

    public String getEnvironment()           { return environment; }
    public void   setEnvironment(String env) { this.environment = env; }

    public Env getNonprod()        { return nonprod; }
    public void setNonprod(Env e)  { this.nonprod = e; }

    public Env getProd()           { return prod; }
    public void setProd(Env e)     { this.prod = e; }

    /** Returns the active environment block based on {@code ldap.environment}. */
    public Env active() {
        return "prod".equalsIgnoreCase(environment) ? prod : nonprod;
    }

    // ── Inner record ─────────────────────────────────────────────────

    public static class Env {

        /** LDAP URL, e.g. ldap://dc01.company.com:389 */
        private String server = "ldap://localhost:389";

        /** NTLM domain prefix used for bind: DOMAIN\username */
        private String domain = "COMPANY";

        /** Base DN for user searches. */
        private String baseDn = "DC=company,DC=com";

        /**
         * Full DN of the AD group that grants access.
         * Set to empty string to skip group-membership check.
         */
        private String requiredGroup = "";

        /**
         * When true, uses LDAP_MATCHING_RULE_IN_CHAIN
         * (OID 1.2.840.113556.1.4.1941) so nested AD group membership
         * is resolved automatically.
         */
        private boolean recursiveGroup = true;

        public String  getServer()               { return server; }
        public void    setServer(String s)        { this.server = s; }

        public String  getDomain()               { return domain; }
        public void    setDomain(String d)        { this.domain = d; }

        public String  getBaseDn()               { return baseDn; }
        public void    setBaseDn(String b)        { this.baseDn = b; }

        public String  getRequiredGroup()         { return requiredGroup; }
        public void    setRequiredGroup(String g) { this.requiredGroup = g; }

        public boolean isRecursiveGroup()         { return recursiveGroup; }
        public void    setRecursiveGroup(boolean r){ this.recursiveGroup = r; }
    }
}
