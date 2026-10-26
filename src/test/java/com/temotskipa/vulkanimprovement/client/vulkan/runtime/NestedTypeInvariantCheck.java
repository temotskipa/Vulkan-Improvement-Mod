package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Holds every type of the mod to a file of its own: a class, record, interface or enum declared in
 * another type or in a method fails the build. The code style imports a nested class by its own
 * name, so a nested type of the mod's clashes with a nested type of Minecraft's of the same name,
 * as {@code ModelGpuRecords.Polygon} did with {@code ModelPart.Polygon}. Anonymous classes stay
 * allowed.
 */
public final class NestedTypeInvariantCheck {
    private static final List<Path> SOURCE_ROOTS = List.of(Path.of("src/client/java"), Path.of("src/main/java"), Path.of("src/test/java"));
    
    private NestedTypeInvariantCheck() {
    }
    
    static void main() throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : SOURCE_ROOTS) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
            }
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> nested = new ArrayList<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, null, List.of("-proc:none"), null, fileManager.getJavaFileObjectsFromPaths(files));
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitClass(ClassTree tree, Void unused) {
                        boolean topLevel = getCurrentPath().getParentPath().getLeaf() instanceof CompilationUnitTree;
                        if (!topLevel && !tree.getSimpleName().isEmpty()) {
                            nested.add(unit.getSourceFile().getName() + ":" + unit.getLineMap().getLineNumber(positions.getStartPosition(unit, tree)) + " " + tree.getSimpleName());
                        }
                        return super.visitClass(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
        require(files.size() > 100, "the check must find the mod's sources (found " + files.size() + ")");
        require(nested.isEmpty(), "every type must have a file of its own, but these are nested: " + nested);
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}