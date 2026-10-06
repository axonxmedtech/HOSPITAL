package com.hms.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupInitializationStateTest {

    @Test
    void startsAsStartingAndReportsOutOfService() {
        StartupInitializationState state = new StartupInitializationState(true);

        assertThat(state.phase()).isEqualTo(StartupInitializationState.Phase.STARTING);
        assertThat(state.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void completeReportsUp() {
        StartupInitializationState state = new StartupInitializationState(true);

        state.complete();

        assertThat(state.phase()).isEqualTo(StartupInitializationState.Phase.COMPLETE);
        assertThat(state.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void failReportsDown_andCarriesNoDetailsIntoTheHealthResponse() {
        StartupInitializationState state = new StartupInitializationState(true);

        state.fail(List.of("missing required schema: icu_stay (id)"));

        assertThat(state.phase()).isEqualTo(StartupInitializationState.Phase.FAILED);
        assertThat(state.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(state.health().getDetails()).as("the reason is logged, never exposed").isEmpty();
    }

    @Test
    void aFailureIsTerminal_aLaterCompleteDoesNotHideIt() {
        StartupInitializationState state = new StartupInitializationState(true);

        state.fail(List.of("startup step failed: IllegalStateException"));
        state.complete();

        assertThat(state.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void withTheRunnerDisabledThereIsNothingToWaitFor() {
        StartupInitializationState state = new StartupInitializationState(false);

        assertThat(state.phase()).isEqualTo(StartupInitializationState.Phase.COMPLETE);
        assertThat(state.health().getStatus()).isEqualTo(Status.UP);
    }
}
