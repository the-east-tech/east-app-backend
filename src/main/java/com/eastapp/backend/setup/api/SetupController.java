package com.eastapp.backend.setup.api;

import com.eastapp.backend.setup.InitialSetupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final InitialSetupService initialSetupService;

    public SetupController(InitialSetupService initialSetupService) {
        this.initialSetupService = initialSetupService;
    }

    @GetMapping("/status")
    SetupStatusResponse status() {
        return initialSetupService.status();
    }

    @PostMapping("/owner")
    CompleteInitialSetupResponse complete(
            @Valid @RequestBody CompleteInitialSetupRequest request
    ) {
        return initialSetupService.complete(request);
    }
}
