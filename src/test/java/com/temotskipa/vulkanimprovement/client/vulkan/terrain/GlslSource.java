package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what the terrain shaders declare, for checks that the Java side agrees with them rather
 * than with constants of its own.
 */
final class GlslSource {
    static final Path SHADERS = Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain");
    private static final Pattern MEMBER = Pattern.compile("(?:layout\\([^)]*\\)\\s*)?(\\w+)\\s+(\\w+)\\s*(\\[])?\\s*;");
    private static final Map<String, Integer> BYTES = Map.of("int", 4, "uint", 4, "float", 4, "vec2", 8, "vec3", 12, "vec4", 16, "ivec4", 16, "uvec4", 16, "uint64_t", 8);
    
    private GlslSource() {
    }
    
    static String read(String file) throws IOException {
        return Files.readString(SHADERS.resolve(file)).replace("\r", "");
    }
    
    /**
     * What lies between the text that opens a block, such as a push-constant block's declaration
     * up to its brace, and the brace that closes it.
     */
    static String body(String source, String opening) {
        int start = source.indexOf(opening);
        require(start >= 0, "missing " + opening);
        int end = source.indexOf('}', start + opening.length());
        require(end > start, "unclosed " + opening);
        return source.substring(start + opening.length(), end);
    }
    
    /**
     * A block's members, up to a runtime-sized array, which takes no room of the block's own.
     */
    static List<Member> members(String body) {
        List<Member> members = new ArrayList<>();
        Matcher member = MEMBER.matcher(body.replaceAll("//[^\n]*", ""));
        while (member.find()) {
            if (member.group(3) != null) {
                break;
            }
            Integer bytes = BYTES.get(member.group(1));
            require(bytes != null, "unknown GLSL type " + member.group(1) + " of " + member.group(2));
            members.add(new Member(member.group(1), member.group(2), bytes));
        }
        return members;
    }
    
    /**
     * How many bytes a block's members take, laid out one after another as the scalar layout does.
     */
    static int bytes(String body) {
        return members(body).stream().mapToInt(Member::bytes).sum();
    }
    
    /**
     * The value a layout qualifier gives a name, such as {@code local_size_x} or {@code max_vertices}.
     */
    static int layoutValue(String source, String name) {
        Matcher value = Pattern.compile("\\b" + name + "\\s*=\\s*(\\d+)").matcher(source);
        require(value.find(), "missing layout value " + name);
        return Integer.parseInt(value.group(1));
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
    
    record Member(String type, String name, int bytes) {
    }
}