package ai.forge.server.quality;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class JavaMemberCommentChecker {

    private static final Pattern CHINESE_CHARACTER = Pattern.compile("[\\p{IsHan}]");

    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    List<Violation> check(Path sourceRoot) throws IOException {
        List<Violation> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(this::isProductionJavaSource).sorted().toList()) {
                checkFile(path, violations);
            }
        }
        violations.sort(Comparator.comparing(Violation::path)
                .thenComparingInt(Violation::line)
                .thenComparing(Violation::member));
        return List.copyOf(violations);
    }

    private boolean isProductionJavaSource(Path path) {
        String normalizedPath = path.toString().replace('\\', '/');
        return Files.isRegularFile(path)
                && normalizedPath.endsWith(".java")
                && !normalizedPath.contains("/generated/")
                && !normalizedPath.contains("/generated-sources/");
    }

    private void checkFile(Path path, List<Violation> violations) throws IOException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        var parseResult = parser.parse(source);
        CompilationUnit compilationUnit = parseResult.getResult()
                .orElseThrow(() -> new IllegalArgumentException(
                        "无法解析 Java 源码 " + path + ": " + parseResult.getProblems()));

        for (FieldDeclaration field : compilationUnit.findAll(FieldDeclaration.class)) {
            if (field.getVariables().size() != 1) {
                for (var variable : field.getVariables()) {
                    violations.add(violation(
                            path,
                            field,
                            variable.getNameAsString(),
                            "一个声明包含多个字段，无法为每个字段提供独立注释"));
                }
                continue;
            }
            checkComment(
                    path,
                    source,
                    annotatedStart(field, field.getAnnotations()),
                    field,
                    field.getVariable(0).getNameAsString(),
                    violations);
        }

        for (RecordDeclaration record : compilationUnit.findAll(RecordDeclaration.class)) {
            for (Parameter component : record.getParameters()) {
                checkComment(
                        path,
                        source,
                        annotatedStart(component, component.getAnnotations()),
                        component,
                        component.getNameAsString(),
                        violations);
            }
        }

        for (EnumConstantDeclaration constant : compilationUnit.findAll(EnumConstantDeclaration.class)) {
            checkComment(
                    path,
                    source,
                    annotatedStart(constant, constant.getAnnotations()),
                    constant,
                    constant.getNameAsString(),
                    violations);
        }
    }

    private Node annotatedStart(Node declaration, NodeList<AnnotationExpr> annotations) {
        if (annotations.isEmpty()) {
            return declaration;
        }
        return annotations.get(0);
    }

    private void checkComment(
            Path path,
            String source,
            Node commentTarget,
            Node declaration,
            String member,
            List<Violation> violations) {
        int offset = offsetOf(source, commentTarget);
        String rawPrefix = source.substring(0, offset);
        String prefix = rawPrefix.stripTrailing();
        if (!prefix.endsWith("*/")) {
            violations.add(violation(path, declaration, member, "缺少紧邻声明的普通块注释"));
            return;
        }

        String separation = rawPrefix.substring(prefix.length());
        long lineBreaks = separation.chars().filter(character -> character == '\n').count();
        if (lineBreaks > 1) {
            violations.add(violation(path, declaration, member, "普通块注释与声明之间不能存在空行"));
            return;
        }

        int commentStart = prefix.lastIndexOf("/*");
        if (commentStart < 0) {
            violations.add(violation(path, declaration, member, "缺少紧邻声明的普通块注释"));
            return;
        }

        String comment = prefix.substring(commentStart);
        if (comment.startsWith("/**")) {
            violations.add(violation(path, declaration, member, "Javadoc 不能替代成员普通块注释"));
            return;
        }
        if (!CHINESE_CHARACTER.matcher(comment).find()) {
            violations.add(violation(path, declaration, member, "普通块注释必须包含中文语义说明"));
        }
    }

    private int offsetOf(String source, Node node) {
        var position = node.getBegin().orElseThrow();
        int offset = 0;
        for (int line = 1; line < position.line; line++) {
            int newline = source.indexOf('\n', offset);
            if (newline < 0) {
                throw new IllegalStateException("源码位置超出文件范围");
            }
            offset = newline + 1;
        }
        return offset + position.column - 1;
    }

    private Violation violation(Path path, Node node, String member, String reason) {
        int line = node.getBegin().map(position -> position.line).orElse(1);
        return new Violation(path.toString(), line, member, reason);
    }

    record Violation(String path, int line, String member, String reason) {

        @Override
        public String toString() {
            return path + ":" + line + " member=" + member + " " + reason;
        }
    }
}
