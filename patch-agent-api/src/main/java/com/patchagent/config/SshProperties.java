package com.patchagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssh")
public class SshProperties {

    private String username = "root";
    private String password = "";
    private String privateKeyPath = "";
    private int port = 22;
    private int connectTimeout = 15;
    private int commandTimeout = 30;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getCommandTimeout() { return commandTimeout; }
    public void setCommandTimeout(int commandTimeout) { this.commandTimeout = commandTimeout; }
}
