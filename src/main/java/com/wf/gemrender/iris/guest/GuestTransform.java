package com.wf.gemrender.iris.guest;

import com.wf.gemrender.GemRender;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.LiteralExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.initializer.ExpressionInitializer;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.LayoutQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.LayoutQualifierPart;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.NamedLayoutQualifierPart;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifier;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.TypeQualifierPart;
import io.github.douira.glsl_transformer.ast.print.ASTPrinter;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Iris-patched pack stages -> guest stages. Vertex: every {@code in} demoted to a global, assigned from the skinned
 * copy before the pack's {@code main}. Fragment (OIT producers): every {@code out} demoted, read back by location.
 * Render thread only.
 */
final class GuestTransform {
    private static final Pattern VERSION_LINE = Pattern.compile("#version\\s+(\\d+)[^\\n]*\\n");
    private static final String PACK_MAIN = "_gr_irisMain";

    /** Iris vertex input -> {@code guest.vert} value; cast through the declared type. */
    private static final Map<String, String> INPUTS = Map.ofEntries(
            Map.entry("iris_Position", "_gr_gPosition"),
            Map.entry("iris_Color", "_gr_color"),
            Map.entry("iris_Normal", "_gr_gNormal"),
            Map.entry("iris_UV0", "_gr_gUv"),
            Map.entry("iris_UV1", "_gr_gOverlay"),
            Map.entry("iris_UV2", "_gr_gLight"),
            // Batched across entities: no per-copy ids. Iris writes -1 for "none".
            Map.entry("iris_Entity", "ivec4(-1, -1, -1, 0)"),
            Map.entry("mc_midTexCoord", "_gr_gMidUv"),
            Map.entry("at_tangent", "_gr_gTangent"),
            Map.entry("iris_LineWidth", "vec4(1.0)"));
    /** GL's value for an attribute with no array bound. */
    private static final String UNBOUND = "vec4(0.0, 0.0, 0.0, 1.0)";

    private static final SingleASTTransformer<JobParameters> VERTEX = transformer();
    private static final SingleASTTransformer<JobParameters> FRAGMENT = transformer();
    private static final Set<String> REPORTED = new HashSet<>();

    @Nullable
    private static Interface lastInputs;
    @Nullable
    private static Map<Integer, String> lastOutputs;

    static {
        VERTEX.setTransformation((TranslationUnit tree, Root root) -> lastInputs = demoteInputs(tree, root));
        FRAGMENT.setTransformation((TranslationUnit tree, Root root) -> lastOutputs = demoteOutputs(tree, root));
    }

    private GuestTransform() {
    }

    private static SingleASTTransformer<JobParameters> transformer() {
        SingleASTTransformer<JobParameters> transformer = new SingleASTTransformer<>() {
            @Override
            public TranslationUnit parseTranslationUnit(Root rootInstance, String input) {
                getLexer().version = Version.fromNumber(version(input));
                return super.parseTranslationUnit(rootInstance, input);
            }
        };
        transformer.setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
        transformer.setPrintType(PrintType.SIMPLE);
        return transformer;
    }

    private static int version(String stage) {
        Matcher matcher = VERSION_LINE.matcher(stage);
        if (!matcher.find()) {
            throw new IllegalStateException("Iris output without #version");
        }
        return Integer.parseInt(matcher.group(1));
    }

    static String vertex(String patched, String library) {
        String transformed = VERTEX.transform(patched);
        Interface inputs = lastInputs;
        lastInputs = null;

        StringBuilder out = new StringBuilder(transformed).append('\n')
                .append(library)
                .append("\nvoid main() {\n    _gr_guestVertex();\n");
        inputs.types()
                .forEach((name, type) -> {
                    String source = INPUTS.get(name);
                    if (source == null && REPORTED.add(name)) {
                        GemRender.LOGGER.info("Shaderpack vertex input {} has no guest value; it reads {}", name,
                                UNBOUND);
                    }
                    out.append("    ")
                            .append(name)
                            .append(" = ")
                            .append(type)
                            .append('(')
                            .append(source == null ? UNBOUND : source)
                            .append(");\n");
                });
        inputs.deferred()
                .forEach(statement -> out.append("    ")
                        .append(statement)
                        .append('\n'));
        return out.append("    " + PACK_MAIN + "();\n}\n")
                .toString();
    }

    /** {@code outputs}: location -> demoted global; the pack's {@code main} renamed {@value PACK_MAIN}. */
    record Fragment(String source, Map<Integer, String> outputs) {
    }

    static Fragment demote(String patched) {
        String transformed = FRAGMENT.transform(patched);
        Map<Integer, String> outputs = lastOutputs;
        lastOutputs = null;
        return new Fragment(transformed, outputs);
    }

    private record Interface(Map<String, String> types, List<String> deferred) {
    }

    private static Interface demoteInputs(TranslationUnit tree, Root root) {
        Map<String, String> types = new LinkedHashMap<>();
        for (ExternalDeclaration external : tree.getChildren()) {
            if (!(external instanceof DeclarationExternalDeclaration declaration)
                    || !(declaration.getDeclaration() instanceof TypeAndInitDeclaration typed)
                    || !hasStorage(typed.getType()
                    .getTypeQualifier(), StorageQualifier.StorageType.IN)) {
                continue;
            }
            String type = ASTPrinter.printSimple(typed.getType()
                            .getTypeSpecifier())
                    .strip();
            for (DeclarationMember member : typed.getMembers()) {
                types.put(member.getName()
                        .getName(), type);
            }
            typed.getType()
                    .setTypeQualifier(null);
        }

        // Global initialisers reading a demoted input run after the assignment instead.
        List<String> deferred = new ArrayList<>();
        if (!types.isEmpty()) {
            Pattern reference = Pattern.compile("\\b(" + String.join("|", types.keySet()) + ")\\b");
            for (ExternalDeclaration external : tree.getChildren()) {
                if (!(external instanceof DeclarationExternalDeclaration declaration)
                        || !(declaration.getDeclaration() instanceof TypeAndInitDeclaration typed)) {
                    continue;
                }
                TypeQualifier qualifier = typed.getType()
                        .getTypeQualifier();
                if (hasStorage(qualifier, StorageQualifier.StorageType.CONST)
                        || hasStorage(qualifier, StorageQualifier.StorageType.UNIFORM)) {
                    continue;
                }
                for (DeclarationMember member : typed.getMembers()) {
                    if (!(member.getInitializer() instanceof ExpressionInitializer initializer)) {
                        continue;
                    }
                    String expression = ASTPrinter.printSimple(initializer.getExpression())
                            .strip();
                    if (reference.matcher(expression)
                            .find()) {
                        deferred.add(member.getName()
                                .getName() + " = " + expression + ";");
                        member.setInitializer(null);
                    }
                }
            }
        }

        root.rename("main", PACK_MAIN);
        return new Interface(types, deferred);
    }

    private static Map<Integer, String> demoteOutputs(TranslationUnit tree, Root root) {
        Map<Integer, String> outputs = new HashMap<>();
        for (ExternalDeclaration external : tree.getChildren()) {
            if (!(external instanceof DeclarationExternalDeclaration declaration)
                    || !(declaration.getDeclaration() instanceof TypeAndInitDeclaration typed)
                    || !hasStorage(typed.getType()
                    .getTypeQualifier(), StorageQualifier.StorageType.OUT)) {
                continue;
            }
            if (typed.getMembers()
                    .size() != 1) {
                throw new IllegalStateException("Fragment output declaration with several members");
            }
            String name = typed.getMembers()
                    .get(0)
                    .getName()
                    .getName();
            if (outputs.put(location(typed.getType()
                    .getTypeQualifier(), name), name) != null) {
                throw new IllegalStateException("Fragment outputs share a location: " + name);
            }
            typed.getType()
                    .setTypeQualifier(null);
        }
        root.rename("main", PACK_MAIN);
        return outputs;
    }

    private static int location(TypeQualifier qualifier, String output) {
        for (TypeQualifierPart part : qualifier.getParts()) {
            if (part instanceof LayoutQualifier layout) {
                for (LayoutQualifierPart layoutPart : layout.getParts()) {
                    if (layoutPart instanceof NamedLayoutQualifierPart named && named.getName()
                            .getName()
                            .equals("location") && named.getExpression() instanceof LiteralExpression literal) {
                        return (int) literal.getInteger();
                    }
                }
            }
        }
        throw new IllegalStateException("Fragment output " + output + " has no layout location");
    }

    private static boolean hasStorage(@Nullable TypeQualifier qualifier, StorageQualifier.StorageType type) {
        if (qualifier == null) {
            return false;
        }
        for (TypeQualifierPart part : qualifier.getParts()) {
            if (part instanceof StorageQualifier storage && storage.storageType == type) {
                return true;
            }
        }
        return false;
    }
}
