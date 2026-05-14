package com.patchagent.service;

import com.patchagent.config.LdapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Service;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Hashtable;

/**
 * Authenticates users against Active Directory via LDAP.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Perform an NTLM bind with {@code DOMAIN\username} + password to
 *       verify credentials are valid.</li>
 *   <li>If {@code required-group} is non-empty, search for the user with
 *       a {@code memberOf:1.2.840.113556.1.4.1941:=} filter (recursive
 *       group resolution) to confirm group membership.</li>
 * </ol>
 */
@Service
public class LdapAuthService {

    private static final Logger log = LoggerFactory.getLogger(LdapAuthService.class);

    /** OID for LDAP_MATCHING_RULE_IN_CHAIN — resolves nested AD groups. */
    private static final String MATCHING_RULE_IN_CHAIN = "1.2.840.113556.1.4.1941";

    private final LdapProperties ldapProperties;

    public LdapAuthService(LdapProperties ldapProperties) {
        this.ldapProperties = ldapProperties;
    }

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Validates credentials against Active Directory for the given environment.
     *
     * @param username     bare username (no domain prefix) entered by the user
     * @param password     plaintext password
     * @param environment  "nonprod" or "prod" — selects the LDAP config block
     * @return {@link AuthResult} — check {@link AuthResult#success()} before using
     */
    public AuthResult authenticate(String username, String password, String environment) {
        LdapProperties.Env cfg = "prod".equalsIgnoreCase(environment)
                ? ldapProperties.getProd()
                : ldapProperties.getNonprod();

        String bindDn = cfg.getDomain() + "\\" + username;
        DirContext ctx = null;

        try {
            ctx = bindAsUser(cfg.getServer(), bindDn, password);
        } catch (AuthenticationException ex) {
            log.info("LDAP auth failed for user '{}': {}", username, ex.getMessage());
            return AuthResult.failure("Invalid username or password.");
        } catch (NamingException ex) {
            log.warn("LDAP connection error for user '{}': {}", username, ex.getMessage());
            return AuthResult.failure("Unable to contact authentication server. Please try again.");
        }

        try {
            // Credentials are valid — now check group membership if required
            String requiredGroup = cfg.getRequiredGroup();
            if (requiredGroup != null && !requiredGroup.isBlank()) {
                boolean member = isGroupMember(ctx, cfg.getBaseDn(), username,
                        requiredGroup, cfg.isRecursiveGroup());
                if (!member) {
                    log.info("User '{}' authenticated but is not a member of '{}'",
                            username, requiredGroup);
                    return AuthResult.failure("Access denied: your account is not authorised to use Patch Agent.");
                }
            }
            log.info("LDAP auth succeeded for user '{}'", username);
            return AuthResult.success();
        } catch (NamingException ex) {
            log.warn("LDAP group membership check failed for '{}': {}", username, ex.getMessage());
            return AuthResult.failure("Group membership check failed. Please contact your administrator.");
        } finally {
            close(ctx);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────

    /** Open an LDAP connection using plain NTLM bind. */
    private DirContext bindAsUser(String serverUrl, String bindDn, String password)
            throws NamingException {

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL,             serverUrl);
        env.put(Context.SECURITY_AUTHENTICATION,  "simple");
        env.put(Context.SECURITY_PRINCIPAL,       bindDn);
        env.put(Context.SECURITY_CREDENTIALS,     password);
        // Connection timeout (ms)
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout",    "10000");

        return new InitialDirContext(env);
    }

    /**
     * Returns true if {@code username} is (directly or transitively) a member
     * of {@code groupDn}.
     *
     * <p>When {@code recursive} is true an extensibleMatch filter using the
     * {@code LDAP_MATCHING_RULE_IN_CHAIN} OID is used, which causes Active
     * Directory to resolve nested group membership server-side.
     */
    private boolean isGroupMember(DirContext ctx, String baseDn,
                                  String username, String groupDn,
                                  boolean recursive) throws NamingException {

        String memberOfAttr;
        if (recursive) {
            // Recursive: matches the group and all groups it is transitively a member of
            memberOfAttr = "memberOf:" + MATCHING_RULE_IN_CHAIN + ":=";
        } else {
            memberOfAttr = "memberOf=";
        }

        // Escape special LDAP chars in groupDn just in case
        String filter = String.format(
                "(&(objectClass=user)(sAMAccountName=%s)(%s%s))",
                escapeLdap(username), memberOfAttr, groupDn);

        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setReturningAttributes(new String[]{"sAMAccountName"});
        sc.setCountLimit(1);

        NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, sc);
        try {
            return results.hasMoreElements();
        } finally {
            results.close();
        }
    }

    /** Escape special characters in LDAP filter values per RFC 4515. */
    private static String escapeLdap(String value) {
        return value
                .replace("\\", "\\5c")
                .replace("*",  "\\2a")
                .replace("(",  "\\28")
                .replace(")",  "\\29")
                .replace("\0", "\\00");
    }

    private static void close(DirContext ctx) {
        if (ctx != null) {
            try { ctx.close(); } catch (NamingException ignored) {}
        }
    }

    // ── Result type ──────────────────────────────────────────────────

    public record AuthResult(boolean success, String errorMessage) {
        public static AuthResult success()            { return new AuthResult(true, null); }
        public static AuthResult failure(String msg)  { return new AuthResult(false, msg); }
    }
}
