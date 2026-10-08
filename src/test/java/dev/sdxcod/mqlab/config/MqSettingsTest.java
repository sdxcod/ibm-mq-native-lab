package dev.sdxcod.mqlab.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MqSettingsTest {
    @TempDir
    Path directory;

    private MqSettings settings(String password, Path file) {
        return new MqSettings(
                "localhost",
                1414,
                "QM1",
                "DEV.APP.SVRCONN",
                "app",
                password,
                file,
                "DEV.LAB.IN",
                "DEV.LAB.OUT",
                "DEV.LAB.TEST",
                1048576);
    }

    @Test
    void loadsExactFileSecretWithoutTrimmingAndEnvironmentOverrideWins() throws Exception {
        Path file = directory.resolve("password");
        Files.writeString(file, " leading secret ");
        assertEquals(" leading secret ", settings("", file).resolvePassword());
        assertEquals("override", settings("override", file).resolvePassword());
    }

    @Test
    void missingSecretIsActionableAndQueueAliasesAreRestricted() {
        assertTrue(assertThrows(java.io.IOException.class,
                () -> settings("", directory.resolve("absent"))
                        .resolvePassword())
                .getMessage()
                .contains("init-lab.sh"));
        assertEquals("DEV.LAB.OUT", settings("x", null).queue("out"));
        assertThrows(IllegalArgumentException.class, () -> settings("x", null).queue("arbitrary"));
    }
}
