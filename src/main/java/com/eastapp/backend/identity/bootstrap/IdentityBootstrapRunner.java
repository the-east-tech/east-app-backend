package com.eastapp.backend.identity.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class IdentityBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityBootstrapRunner.class);

    private final BootstrapProperties properties;
    private final IdentityBootstrapService bootstrapService;

    public IdentityBootstrapRunner(
            BootstrapProperties properties,
            IdentityBootstrapService bootstrapService
    ) {
        this.properties = properties;
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.isEnabled()) {
            return;
        }

        IdentityBootstrapService.BootstrapResult result =
                bootstrapService.bootstrap(properties);

        LOGGER.info(
                "Identity bootstrap ready for companyCode={} employeeId={} userCreated={} secondaryHeadCreated={} contextProfilesCreated={}",
                result.companyCode(),
                result.employeeId(),
                result.userCreated(),
                result.secondaryHeadCreated(),
                result.contextProfilesCreated()
        );
    }
}
