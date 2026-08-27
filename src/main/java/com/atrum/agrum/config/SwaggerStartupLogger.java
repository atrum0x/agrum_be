package com.atrum.agrum.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SwaggerStartupLogger implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerStartupLogger.class);
    private final Environment environment;

    public SwaggerStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String port = environment.getProperty("server.port", "8080");
        logger.info("\n----------------------------------------------------------\n\t" +
                "Swagger UI is running! Access Documentation at:\n\t" +
                "Local: \t\thttp://localhost:{}/swagger-ui/index.html\n\t" +
                "----------------------------------------------------------", port);
    }
}
