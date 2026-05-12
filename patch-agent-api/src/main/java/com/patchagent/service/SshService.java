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
     * Run a command on a remote host. Returns [exitCode, stdout, stderr].
     */
    public CommandResult run(String host, String command) throws JSchException, IOException {
        Session session = connect(host);
        try {
            return execute(session, command, props.getCommandTimeout() * 1000L);
        } finally {
            session.disconnect();
        }
    }

    /**
     * Check the live status of a service row.
     * Returns "running", "stopped", "error", or "unknown".
     */
    public String checkStatus(ServerRow row) {
        String statusCmd = row.getStatusCmd();
        if (statusCmd == null || statusCmd.isBlank()) return "unknown";

        try {
            CommandResult result = run(row.getHost(), statusCmd);
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
        JSch jsch = new JSch();

        // Key-based auth
        String keyPath = props.getPrivateKeyPath();
        if (keyPath != null && !keyPath.isBlank()) {
            String expanded = keyPath.replace("~", System.getProperty("user.home"));
            jsch.addIdentity(expanded);
        }

        Session session = jsch.getSession(props.getUsername(), host, props.getPort());

        // Password auth (used if no key configured)
        String password = props.getPassword();
        if (password != null && !password.isBlank()) {
            session.setPassword(password);
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
