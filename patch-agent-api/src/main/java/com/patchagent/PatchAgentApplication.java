package com.patchagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.patchagent.config.SshProperties;
import com.patchagent.config.PatchingProperties;

@SpringBootApplication
@EnableConfigurationProperties({SshProperties.class, PatchingProperties.class})
public class PatchAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatchAgentApplication.class, args);
    }
}
