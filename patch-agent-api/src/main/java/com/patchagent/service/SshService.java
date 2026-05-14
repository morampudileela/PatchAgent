package com.patchagent.service;

import com.jcraft.jsch.*;
import com.patchagent.config.SshProperties;
import com.patchagent.model.ServerRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * SSH client service wrapping JSch (com.github.mwiede:jsch).
 * Mirrors Python's _ssh_connect(), _run(), and _check_status().
 */
@Service
public class SshService {

    private static final Logger log = LoggerFactory.getLogger(SshService.class);

    private final SshProperties props;

    public SshService(SshProperties props) {
        this.props = props;
    }

    // ---------------------------------------------------------------------------
    //  Public API
    // ---------------------------------------------------------------------------

    /**
     * Run a command on a remote host using config-file credentials.
     */
    public CommandResult run(String host, String command) throws JSchException, IOException {
        return run(host, command, null, null);
    }

    /**
     * Run a command on a remote host, preferring the supplied session credentials
     * over the config-file defaults.  Pass {@code null} for both to fall back to
     * config.
     */
    public CommandResult run(String host, String command,
                             String username, String password)
            throws JSchException, IOException {
        Session session = connect(host, username, password);
        try {
            return execute(session, command, props.getCommandTimeout() * 1000L);
        } finally {
            session.disconnect();
        }
    }

    /**
     * Check the live status of a service row using config-file credentials.
     */
    public String checkStatus(ServerRow row) {
        return checkStatus(row, null, null);
    }

    /**
     * Check the live status of a service row, preferring the supplied session
     * credentials over the config-file defaults.
     */
    public String checkStatus(ServerRow row, String username, String password) {
        String statusCmd = row.getStatusCmd();
        if (statusCmd == null || statusCmd.isBlank()) return "unknown";

        try {
            CommandResult result = run(row.getHost(), statusCmd, username, password);
            return result.exitCode() == 0 ? "running" : "stopped";
        } catch (Exception e) {
            log.debug("Status check failed for {}: {}", row.getHost(), e.getMessage());
            return "error";
        }
    }

    // ---------------------------------------------------------------------------
    //  Internal helpers
    // ---------------------------------------------------------------------------

    Session connect(String host) throws JSchException {
        return connect(host, null, null);
    }

    /**
     * Open a JSch session. When {@code username}/{@code password} are non-blank they
     * take priority over the values in {@link SshProperties} (config-file defaults).
     * This allows per-request AD credentials to be used without touching the config.
     */
    Session connect(String host, String username, String password) throws JSchException {
        JSch jsch = new JSch();

        // Effective username: session credential > config
        String effectiveUser = (username != null && !username.isBlank())
                ? username : props.getUsername();

        Session session = jsch.getSession(effectiveUser, host, props.getPort());

        // Key-based auth (config only — session credentials always use password)
        String keyPath = props.getPrivateKeyPath();
        if ((username == null || username.isBlank()) && keyPath != null && !keyPath.isBlank()) {
            String expanded = keyPath.replace("~", System.getProperty("user.home"));
            jsch.addIdentity(expanded);
        }

        // Effective password: session credential > config
        String effectivePassword = (password != null && !password.isBlank())
                ? password : props.getPassword();
        if (effectivePassword != null && !effectivePassword.isBlank()) {
            session.setPassword(effectivePassword);
        }

        // Disable strict host key checking (mirrors paramiko AutoAddPolicy)
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "publickey,password");
        session.setConfig(config);

        session.setTimeout(props.getConnectTimeout() * 1000);
        session.connect(props.getConnectTimeout() * 1000);
        return session;
    }

    private CommandResult execute(Session session, String command, long timeoutMs)
            throws JSchException, IOException {

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setErrStream(null);  // we read stderr manually

        InputStream stdout = channel.getInputStream();
        InputStream stderr = channel.getErrStream();

        channel.connect();

        // Read stdout and stderr while waiting for exit
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (!channel.isClosed()) {
            drainStream(stdout, outBuf, buf);
            drainStream(stderr, errBuf, buf);
            if (System.currentTimeMillis() > deadline) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        drainStream(stdout, outBuf, buf);
        drainStream(stderr, errBuf, buf);

        int exitCode = channel.getExitStatus();
        channel.disconnect();

        return new CommandResult(
            exitCode,
            outBuf.toString(StandardCharsets.UTF_8).strip(),
            errBuf.toString(StandardCharsets.UTF_8).strip()
        );
    }

    private void drainStream(InputStream in, ByteArrayOutputStream out, byte[] buf)
            throws IOException {
        while (in.available() > 0) {
            int n = in.read(buf);
            if (n > 0) out.write(buf, 0, n);
        }
    }

    // ---------------------------------------------------------------------------
    //  Result record
    // ---------------------------------------------------------------------------

    public record CommandResult(int exitCode, String stdout, String stderr) {
        public boolean success() { return exitCode == 0; }
        public String errorMessage() {
            if (!stderr.isBlank()) return stderr;
            if (!stdout.isBlank()) return stdout;
            return "exit code " + exitCode;
        }
    }
}
