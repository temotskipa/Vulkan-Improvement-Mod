package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DependencyBaselineInvariantCheck {
    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");
    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    private static final Path WRAPPER_PROPERTIES = Path.of("gradle/wrapper/gradle-wrapper.properties");
    private static final Path PORT_PLAN = Path.of("docs/exec-plans/active/minecraft-26-4-snapshot-port.md");
    private static final Path COMPLETED_DEPENDENCY_PLAN = Path.of("docs/exec-plans/completed/minecraft-26-2-dependency-update.md");
    
    private DependencyBaselineInvariantCheck() {
    }
    
    
    static void main(String[] args) throws IOException {
        Properties gradleProperties = new Properties();
        try (var input = Files.newInputStream(GRADLE_PROPERTIES)) {
            gradleProperties.load(input);
        }
        
        String minecraftVersion = requiredProperty(gradleProperties, "minecraft_version");
        String loaderVersion = requiredProperty(gradleProperties, "loader_version");
        String fabricVersion = requiredProperty(gradleProperties, "fabric_version");
        String buildGradle = read(BUILD_GRADLE);
        String wrapperProperties = read(WRAPPER_PROPERTIES);
        String portPlan = read(PORT_PLAN);
        String completedDependencyPlan = read(COMPLETED_DEPENDENCY_PLAN);
        
        // This branch ports the mod to the 26.4 snapshots. Its baseline is the snapshot the port plan
        // names; the 26.2 baseline is recorded in the completed dependency plan.
        require("26.4-snapshot-3".equals(minecraftVersion), "Minecraft baseline must be the snapshot the port plan targets");
        require("26.4-alpha.3".equals(requiredProperty(gradleProperties, "minecraft_dependency")), "the metadata must ask for the name Fabric Loader gives that snapshot");
        require("0.19.5".equals(loaderVersion), "Fabric Loader baseline must match the port plan");
        require("0.162.2+26.4".equals(fabricVersion), "Fabric API baseline must match the port plan");
        requireContains(buildGradle, "vmArg \"-XX:StackShadowPages=32\"", "run configurations must pass the stack setting the 26.4 launcher metadata starts the game with");
        requireContains(buildGradle, "id 'net.fabricmc.fabric-loom' version '1.18-SNAPSHOT'", "Loom plugin version must match the checked dependency audit");
        requireContains(wrapperProperties, "gradle-9.8.1-bin.zip", "Gradle wrapper must match the checked dependency audit");
        for (String pin : new String[]{"`26.4-snapshot-3`", "`26.4-alpha.3`", "`0.19.5`", "`0.162.2+26.4`", "`1.18-SNAPSHOT`", "`9.8.1`", "-XX:StackShadowPages=32"}) {
            requireContains(portPlan, pin, "the port plan must record the current dependency baseline");
        }
        requireContains(completedDependencyPlan, "| Fabric API | `0.154.0+26.2`", "completed dependency plan must record the latest 26.2 Fabric API audit result");
        requireContains(completedDependencyPlan, "| Gradle wrapper | `9.6.1`", "completed dependency plan must record the latest Gradle wrapper audit result");
        requireContains(completedDependencyPlan, "the 2026-07-04 audit found no pin changes", "completed dependency plan must record the latest no-change dependency audit");
    }
    
    private static String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        require(value != null && !value.isBlank(), "missing required Gradle property: " + key);
        return value;
    }
    
    private static String read(Path path) throws IOException {
        require(Files.isRegularFile(path), "missing dependency baseline artifact: " + path);
        return Files.readString(path);
    }
    
    private static void requireContains(String source, String token, String message) {
        require(source.contains(token), message + " (missing `" + token + "`)");
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}