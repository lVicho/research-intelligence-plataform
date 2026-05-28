package com.researchintelligence.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "authenticatedUserAuditorAware")
public class ResearchIntelligencePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchIntelligencePlatformApplication.class, args);
    }
}
