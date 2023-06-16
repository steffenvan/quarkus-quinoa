package io.quarkiverse.quinoa.deployment.packagemanager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;

import org.jboss.logging.Logger;

import io.quarkiverse.quinoa.deployment.QuinoaConfig;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;

/**
 * Configuration defaults for multiple JS frameworks that can be used to allow for easier adoption with less user configuration.
 */
public enum FrameworkType {

    REACT("build", "start", 3000, Set.of("react-scripts", "react-app-rewired", "craco")),
    VITE("dist", "dev", 5173, Set.of("vite")),
    NEXT("out", "dev", 3000, Set.of("next")),
    ANGULAR("dist/%s", "start", 4200, Set.of("ng")),
    WEB_COMPONENTS("dist", "start", 8003, Set.of("web-dev-server"));

    private static final Logger LOG = Logger.getLogger(FrameworkType.class);

    public static final Set<String> DEV_SCRIPTS = Arrays.stream(values()).map(FrameworkType::getDevScript)
            .collect(Collectors.toCollection(TreeSet::new));

    /**
     * This the Web UI internal build system (webpack, …​) output directory. After the build, Quinoa will take the files from
     * this directory, move them to 'target/quinoa-build' (or build/quinoa-build with Gradle) and serve them at runtime.
     */
    private final String buildDirectory;

    /**
     * The script to run in package.json in dev mode typically "start" or "dev".
     */
    private final String devScript;

    /**
     * Default UI live-coding dev server port (proxy mode).
     */
    private final int devServerPort;

    /**
     * Match package.json scripts to detect this framework in use.
     */
    private final Set<String> packageScripts;

    FrameworkType(String buildDirectory, String devScript, int devServerPort, Set<String> packageScripts) {
        this.buildDirectory = buildDirectory;
        this.devScript = devScript;
        this.devServerPort = devServerPort;
        this.packageScripts = packageScripts;
    }

    public static DetectedFramework detectFramework(LaunchModeBuildItem launchMode, QuinoaConfig config, Path packageJsonFile) {
        // only read package.json if the defaults are in use
        if (config.devServer.port.isPresent() && !QuinoaConfig.DEFAULT_BUILD_DIR.equalsIgnoreCase(config.buildDir)) {
            return new DetectedFramework();
        }
        JsonObject packageJson = null;
        JsonString startScript = null;
        String startCommand = null;
        try (JsonReader reader = Json.createReader(Files.newInputStream(packageJsonFile))) {
            packageJson = reader.readObject();
            JsonObject scripts = packageJson.getJsonObject("scripts");
            if (scripts != null) {
                // loop over all possible start scripts until we find one
                for (String devScript : FrameworkType.DEV_SCRIPTS) {
                    startScript = scripts.getJsonString(devScript);
                    if (startScript != null) {
                        startCommand = devScript;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            LOG.warnf("Quinoa failed to auto-detect the framework from package.json file. %s", e.getMessage());
        }

        if (startScript == null || startCommand == null) {
            LOG.info("Quinoa could not auto-detect the framework from package.json file.");
            return new DetectedFramework();
        }

        // check if we found a script to detect which framework
        final FrameworkType frameworkType = evaluate(startScript.getString());
        if (frameworkType == null) {
            LOG.info("Quinoa could not auto-detect the framework from package.json file.");
            return new DetectedFramework();
        }

        String expectedCommand = frameworkType.getDevScript();
        if (!Objects.equals(startCommand, expectedCommand)) {
            LOG.warnf("%s framework typically defines a '%s` script in package.json file but found '%s' instead.",
                    frameworkType, expectedCommand, startCommand);
        }

        LOG.infof("%s framework automatically detected from package.json file.", frameworkType);
        return new DetectedFramework(frameworkType, packageJson, startCommand);
    }

    /**
     * Try and detect the framework based on the script starting with a command like "vite" or "ng"
     *
     * @param script the script to check
     * @return either NULL if no match or the matching framework if found
     */
    private static FrameworkType evaluate(String script) {
        final String lowerScript = script.toLowerCase(Locale.ROOT);
        for (FrameworkType value : values()) {
            Set<String> commands = value.getPackageScripts();
            for (String command : commands) {
                if (lowerScript.startsWith(command)) {
                    return value;
                }
            }
        }
        return null;
    }

    public String getBuildDirectory() {
        return buildDirectory;
    }

    public String getDevScript() {
        return devScript;
    }

    public Set<String> getPackageScripts() {
        return packageScripts;
    }

    public int getDevServerPort() {
        return devServerPort;
    }
}