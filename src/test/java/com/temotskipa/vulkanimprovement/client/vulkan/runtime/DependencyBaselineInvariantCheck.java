package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DependencyBaselineInvariantCheck {
    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");
    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    private static final Path WRAPPER_PROPERTIES = Path.of("gradle/wrapper/gradle-wrapper.properties");
    
    private DependencyBaselineInvariantCheck() {
    }
    
    
    static void main() throws IOException {
        Properties gradleProperties = new Properties();
        try (var input = Files.newInputStream(GRADLE_PROPERTIES)) {
            gradleProperties.load(input);
        }
        
        String minecraftVersion = requiredProperty(gradleProperties, "minecraft_version");
        String loaderVersion = requiredProperty(gradleProperties, "loader_version");
        String fabricVersion = requiredProperty(gradleProperties, "fabric_version");
        String buildGradle = read(BUILD_GRADLE);
        String wrapperProperties = read(WRAPPER_PROPERTIES);
        
        // The baseline is the 26.4 snapshot the mod was ported to and checked on. A move to another
        // version changes these together with the code that depends on them.
        require("26.4-snapshot-5".equals(minecraftVersion), "Minecraft baseline must be the snapshot the mod was checked on");
        require("26.4-alpha.5".equals(requiredProperty(gradleProperties, "minecraft_dependency")), "the metadata must ask for the name Fabric Loader gives that snapshot");
        require("0.20.1".equals(loaderVersion), "Fabric Loader baseline must be the one the mod was checked with");
        require("0.165.0+26.4".equals(fabricVersion), "Fabric API baseline must be the one the mod was checked with");
        requireContains(buildGradle, "jvmArguments.add(\"-XX:StackShadowPages=32\")", "run configurations must pass the stack setting the 26.4 launcher metadata starts the game with");
        requireContains(buildGradle, "id 'net.fabricmc.fabric-loom' version '1.19.3'", "Loom plugin version must match the checked dependency audit");
        requireContains(wrapperProperties, "gradle-9.8.2-bin.zip", "Gradle wrapper must match the checked dependency audit");
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