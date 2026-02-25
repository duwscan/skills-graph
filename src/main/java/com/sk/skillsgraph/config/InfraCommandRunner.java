package com.sk.skillsgraph.config;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InfraCommandRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfraCommandRunner.class);

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("infra-up")) {
            runCommand(List.of("docker", "compose", "up", "-d"));
            return;
        }
        if (args.containsOption("infra-down")) {
            runCommand(List.of("docker", "compose", "down"));
            return;
        }
        if (args.containsOption("infra-reset")) {
            runCommand(List.of("docker", "compose", "down", "-v"));
            runCommand(List.of("docker", "compose", "up", "-d"));
        }
    }

    private void runCommand(List<String> command) throws IOException, InterruptedException {
        LOGGER.info("Running infrastructure command: {}", String.join(" ", command));
        Process process = new ProcessBuilder(command)
                .inheritIO()
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }
}
