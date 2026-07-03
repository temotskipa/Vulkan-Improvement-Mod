package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DependencyBaselineInvariantCheck {
    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");
    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    private static final Path WRAPPER_PROPERTIES = Path.of("gradle/wrapper/gradle-wrapper.properties");
    private static final Path EXTENSION_PLAN = Path.of("docs/exec-plans/active/26-2-vulkan-renderer-extension.md");
    private static final Path COMPLETED_DEPENDENCY_PLAN = Path.of("docs/exec-plans/completed/minecraft-26-2-dependency-update.md");

    private DependencyBaselineInvariantCheck() {
    }

    @SuppressWarnings("unused")
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
        String extensionPlan = read(EXTENSION_PLAN);
        String completedDependencyPlan = read(COMPLETED_DEPENDENCY_PLAN);

        require("26.2".equals(minecraftVersion), "Minecraft baseline must stay on stable 26.2");
        require("0.19.3".equals(loaderVersion), "Fabric Loader baseline must match the checked dependency audit");
        require("0.154.0+26.2".equals(fabricVersion), "Fabric API baseline must match the checked dependency audit");
        requireContains(buildGradle, "id 'net.fabricmc.fabric-loom' version '1.17.13'",
                "Loom plugin version must match the checked dependency audit");
        requireContains(wrapperProperties, "gradle-9.6.1-bin.zip",
                "Gradle wrapper must match the checked dependency audit");
        requireContains(extensionPlan, "Minecraft `26.2`, Fabric API\n  `0.154.0+26.2`, Fabric Loader `0.19.3`, and Loom `1.17.13`",
                "active renderer extension plan must record the current dependency baseline");
        requireContains(extensionPlan, "The Gradle wrapper is `9.6.1`",
                "active renderer extension plan must record the current Gradle wrapper baseline");
        requireContains(completedDependencyPlan, "| Fabric API | `0.154.0+26.2`",
                "completed dependency plan must record the latest 26.2 Fabric API audit result");
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
