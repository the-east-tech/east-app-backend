package com.eastapp.backend.tasks.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskServiceLinkedSopTests {

    @Test
    void optionalLinkedSopDoesNotReadTheEmptyLookupMapWithANullKey() {
        assertNull(TaskService.linkedSopTitle(Map.of(), null));
    }

    @Test
    void linkedSopTitleIsResolvedWhenTheTaskHasALink() {
        UUID sopId = UUID.randomUUID();

        assertEquals(
                "Opening Checklist",
                TaskService.linkedSopTitle(Map.of(sopId, "Opening Checklist"), sopId)
        );
    }
}
