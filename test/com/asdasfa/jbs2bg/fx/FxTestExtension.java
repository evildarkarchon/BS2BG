package com.asdasfa.jbs2bg.fx;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit companion of {@link FxTestToolkit}: after every test it closes any window the test left showing, checks
 * the headless accessibility tripwire, and fails the test with an uncaught FX-thread exception that no FX body
 * reported. It is registered for every test class through JUnit's extension autodetection
 * ({@code test-resources/junit-platform.properties} plus the {@code META-INF/services} entry), so no FX test can
 * forget it; tests that never start the toolkit pay nothing, because the cleanup does nothing until the toolkit
 * runs. Tests still close their own stages in {@code try/finally}; this is the safety net, not the idiom.
 */
public final class FxTestExtension implements AfterEachCallback {

    /**
     * Runs the harness's after-each cleanup.
     *
     * @param context unused; the cleanup is the same for every test, which lets the harness self-test call it
     *        directly
     * @throws Exception when the cleanup finds a recorded FX-thread failure or a tripped tripwire
     */
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        FxTestToolkit.afterEachTest();
    }
}
