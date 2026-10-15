package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java sources as the source checks read them, with every nested class a source imports by its
 * own name written through its outer classes, as the checks name it: {@code DrawIndirect}
 * imported from {@code ChunkSectionsToRender} reads {@code ChunkSectionsToRender.DrawIndirect}.
 * A check then holds however the source imports the class.
 */
final class JavaSources {
    // An import of a nested class: its outer classes, each with its dot, and its own name.
    private static final Pattern NESTED_IMPORT = Pattern.compile("^import (?:[a-z_]\\w*\\.)+((?:[A-Z]\\w*\\.)+)([A-Z]\\w*);", Pattern.MULTILINE);
    
    private JavaSources() {
    }
    
    static String read(Path path) throws IOException {
        return qualifyImportedNestedClasses(Files.readString(path));
    }
    
    static String qualifyImportedNestedClasses(String source) {
        String qualified = source;
        Matcher nestedImport = NESTED_IMPORT.matcher(source);
        while (nestedImport.find()) {
            String nested = nestedImport.group(2);
            qualified = qualified.replaceAll("(?<![\\w.])" + nested + "(?!\\w)", Matcher.quoteReplacement(nestedImport.group(1) + nested));
        }
        return qualified;
    }
}
