package com.skillsgraph;

import com.skillsgraph.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SkillsGraphApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillsGraphApplication.class);

    private final Environment environment;

    @Value("${server.port:8080}")
    private int port;

    public SkillsGraphApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(SkillsGraphApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String runtimeProfile;
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            runtimeProfile = String.join(",", activeProfiles);
        } else {
            runtimeProfile = environment.getProperty("spring.profiles.default", "development");
        }
        LOGGER.info("Skills Graph API started on port {} ({})", port, runtimeProfile);
    }
}
