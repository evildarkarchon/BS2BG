package com.asdasfa.jbs2bg.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.asdasfa.jbs2bg.project.ProjectSession;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Enforces the production platform bans stated in {@code docs/build/workbench-platform.md} ("Production platform
 * bans") by reading every production class file with the JDK 25 {@code java.lang.classfile} API. Class files name
 * the type each call is made on, so the gate can tell {@code Stage.toFront()} (window activation) from
 * {@code Node.toFront()} (stacking order), which a source regex cannot.
 *
 * <p>Each rule's allowlist is keyed by the declaring class and method that contain the call, and every entry must
 * still match a real call so a moved or deleted exception cannot linger. Adding a rule is one more {@link #RULES}
 * entry. The deferral rule is not transitive: {@code Platform.runLater(this::helper)} where the helper requests focus
 * is not detected, so the docs rule covers that case.
 */
class ProductionPlatformBanGateTest {

    private static final String WORKBENCH_CONTROLLER = "com/asdasfa/jbs2bg/workbench/WorkbenchController";

    private static final String PORTRAIT_VIEWER = "com/asdasfa/jbs2bg/workbench/NpcPortraitViewer";

    private static final String PLATFORM = "javafx/application/Platform";

    private static final String APPEARANCE_SOURCE =
            "com/asdasfa/jbs2bg/workbench/JavaFxAppearancePreferencesSource";

    /**
     * The production bans, numbered as in docs/build/workbench-platform.md.
     */
    static final List<Rule> RULES = List.of(
            Rule.invocations("1", "no isFocused/focusedProperty on any javafx/* type (window activation read)",
                    call -> (call.name().equals("isFocused") || call.name().equals("focusedProperty"))
                            && isOrExtends(call.owner(), "javafx/"),
                    Set.of()),
            Rule.invocations("2", "no requestFocus/toFront on any javafx/stage/* type (window activation)",
                    call -> (call.name().equals("requestFocus") || call.name().equals("toFront"))
                            && isOrExtends(call.owner(), "javafx/stage/"),
                    // The portrait viewer raise when it is already open: synchronous, directly commanded by the
                    // user, and the application is already in the foreground.
                    Set.of(new MethodKey(PORTRAIT_VIEWER, "openViewer"))),
            Rule.invocations("3", "no Platform.getPreferences outside the appearance-preferences source",
                    call -> call.owner().equals(PLATFORM) && call.name().equals("getPreferences"),
                    // The one JavaFX appearance-preferences source. Everything else receives a source through
                    // WorkbenchEnvironment, so tests substitute a fake and never read the host's OS theme.
                    Set.of(new MethodKey(APPEARANCE_SOURCE, "*"))),
            Rule.deferredFocus("4",
                    "no Platform.runLater of a requestFocus method reference or a lambda that calls requestFocus",
                    // The Slider Preset rename editor: ListView click handling can take focus back after startEdit.
                    Set.of(new MethodKey(WORKBENCH_CONTROLLER + "$SliderPresetCell", "startEdit"))));

    @Test
    void productionClassesRespectEveryPlatformBan() throws IOException, URISyntaxException {
        List<ClassModel> production = productionClasses();
        assertFalse(production.isEmpty(), "no production class files were found");
        List<String> failures = new ArrayList<>();
        for (Rule rule : RULES) {
            List<Violation> found = scan(rule, production);
            for (Violation violation : found) {
                if (!rule.allows(violation.site()))
                    failures.add(violation.toString());
            }
            for (MethodKey allowed : rule.allowed()) {
                if (found.stream().noneMatch(violation -> allowed.matches(violation.site())))
                    failures.add("rule " + rule.id() + " allowlist entry " + allowed
                            + " matches no call; remove it or update its key");
            }
        }
        assertTrue(failures.isEmpty(), "production platform bans (docs/build/workbench-platform.md) violated:\n"
                + String.join("\n", failures));
    }

    @Test
    void windowActivationReadsFailRuleOneIncludingThroughAJavaFxSubclass() throws IOException {
        Rule rule = rule("1");

        assertEquals(List.of("nodeFocus -> javafx/scene/Node.isFocused",
                        "windowFocus -> javafx/stage/Window.focusedProperty"),
                describe(scan(rule, List.of(fixture(PlatformBanFixtures.ReadsWindowActivation.class)))));
        assertEquals(List.of("ownFocus -> com/asdasfa/jbs2bg/build/PlatformBanFixtures$InheritedFocusRead.isFocused"),
                describe(scan(rule, List.of(fixture(PlatformBanFixtures.InheritedFocusRead.class)))));
        assertEquals(List.of(), scan(rule, List.of(fixture(PlatformBanFixtures.Permitted.class))));
    }

    @Test
    void windowActivationFailsRuleTwoButNodeStackingAndFocusDoNot() throws IOException {
        Rule rule = rule("2");

        assertEquals(List.of("raise -> javafx/stage/Stage.toFront", "raise -> javafx/stage/Stage.requestFocus",
                        "focusPopup -> javafx/stage/Popup.requestFocus",
                        "deferredRaise -> javafx/stage/Stage.toFront"),
                describe(scan(rule, List.of(fixture(PlatformBanFixtures.ActivatesWindow.class)))));
        assertEquals(List.of(), scan(rule, List.of(fixture(PlatformBanFixtures.Permitted.class))));
    }

    @Test
    void liveAppearancePreferenceReadsFailRuleThreeIncludingAMethodReference() throws IOException {
        Rule rule = rule("3");

        assertEquals(List.of("readNow -> javafx/application/Platform.getPreferences",
                        "readLater -> javafx/application/Platform.getPreferences"),
                describe(scan(rule, List.of(fixture(PlatformBanFixtures.ReadsAppearancePreferences.class)))));
        assertEquals(List.of(), scan(rule, List.of(fixture(PlatformBanFixtures.Permitted.class))));
    }

    @Test
    void deferredFocusRequestsFailRuleFourButOtherDeferralsDoNot() throws IOException {
        Rule rule = rule("4");

        List<Violation> found = scan(rule, List.of(fixture(PlatformBanFixtures.DefersFocus.class)));
        assertEquals(List.of("deferReference -> javafx/application/Platform.runLater(Node.requestFocus)",
                        "deferLambda -> javafx/application/Platform.runLater(lambda calling requestFocus)"),
                describe(found));
        // Not transitive: a lambda that calls a focus-requesting helper is left to the docs rule.
        assertEquals(List.of(), scan(rule, List.of(fixture(PlatformBanFixtures.Permitted.class))));
    }

    @Test
    void allowlistEntriesAreKeyedByDeclaringClassAndMethod() throws IOException {
        Rule rule = rule("2");
        List<Violation> found = scan(rule, List.of(fixture(PlatformBanFixtures.ActivatesWindow.class)));
        MethodKey raise = new MethodKey("com/asdasfa/jbs2bg/build/PlatformBanFixtures$ActivatesWindow", "raise");
        Rule allowingRaise = rule.withAllowed(Set.of(raise));

        assertEquals(List.of("focusPopup -> javafx/stage/Popup.requestFocus",
                        "deferredRaise -> javafx/stage/Stage.toFront"),
                describe(found.stream().filter(violation -> !allowingRaise.allows(violation.site())).toList()));
        assertFalse(new MethodKey("com/asdasfa/jbs2bg/build/PlatformBanFixtures$DefersFocus", "raise")
                .matches(raise), "the same method name in another class is a different key");
    }

    /** Finds one configured rule by its documented number. */
    private static Rule rule(String id) {
        return RULES.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
    }

    /** Renders violations as {@code method -> detail} for compact, order-sensitive assertions. */
    private static List<String> describe(List<Violation> violations) {
        return violations.stream().map(violation -> violation.site().method() + " -> " + violation.detail())
                .toList();
    }

    /** Applies one rule to every method body of the given classes, in class-file order. */
    private static List<Violation> scan(Rule rule, List<ClassModel> classes) {
        List<Violation> violations = new ArrayList<>();
        for (ClassModel model : classes) {
            for (MethodModel method : model.methods()) {
                Optional<CodeModel> code = method.code();
                if (code.isEmpty())
                    continue;
                MethodKey site = new MethodKey(model.thisClass().asInternalName(), method.methodName().stringValue());
                List<Instruction> instructions = instructions(code.orElseThrow());
                for (String detail : rule.matcher().find(model, instructions))
                    violations.add(new Violation(rule.id(), site, detail));
            }
        }
        return violations;
    }

    /** Real instructions only; labels, line numbers and other pseudo-instructions are dropped. */
    private static List<Instruction> instructions(CodeModel code) {
        List<Instruction> instructions = new ArrayList<>();
        for (CodeElement element : code) {
            if (element instanceof Instruction instruction)
                instructions.add(instruction);
        }
        return instructions;
    }

    /**
     * Every member a method body names as a call target: ordinary invocations and the implementation method of a
     * lambda or method reference, so {@code stage::toFront} is checked like {@code stage.toFront()}.
     */
    private static List<CallTarget> callTargets(List<Instruction> instructions) {
        List<CallTarget> targets = new ArrayList<>();
        for (Instruction instruction : instructions) {
            if (instruction instanceof InvokeInstruction invoke)
                targets.add(new CallTarget(invoke.owner().asInternalName(), invoke.name().stringValue()));
            else if (instruction instanceof InvokeDynamicInstruction indy)
                lambdaImplementation(indy).ifPresent(targets::add);
        }
        return targets;
    }

    /** The method a {@code LambdaMetafactory} call site binds, if this invokedynamic is one. */
    private static Optional<CallTarget> lambdaImplementation(InvokeDynamicInstruction indy) {
        if (!indy.bootstrapMethod().owner().descriptorString().equals("Ljava/lang/invoke/LambdaMetafactory;"))
            return Optional.empty();
        for (ConstantDesc argument : indy.bootstrapArgs()) {
            if (argument instanceof DirectMethodHandleDesc handle)
                return Optional.of(new CallTarget(internalName(handle.owner()), handle.methodName()));
        }
        return Optional.empty();
    }

    /** Converts a class descriptor such as {@code Ljavafx/stage/Stage;} to {@code javafx/stage/Stage}. */
    private static String internalName(ClassDesc type) {
        String descriptor = type.descriptorString();
        return descriptor.startsWith("L") ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }

    /**
     * True when {@code internalName} is, or inherits from, a class in the {@code packagePrefix} package tree. javac
     * names the receiver's static type as the call owner, so a production subclass of a JavaFX control calling an
     * inherited {@code isFocused()} would otherwise slip past a package check.
     */
    private static boolean isOrExtends(String internalName, String packagePrefix) {
        if (internalName.startsWith(packagePrefix))
            return true;
        if (internalName.startsWith("["))
            return false;
        try {
            Class<?> type = Class.forName(internalName.replace('/', '.'), false,
                    ProductionPlatformBanGateTest.class.getClassLoader());
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (current.getName().replace('.', '/').startsWith(packagePrefix))
                    return true;
            }
            return false;
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("cannot resolve call owner " + internalName, exception);
        }
    }

    /**
     * Rule 4's matcher: a {@code Platform.runLater} whose runnable is created immediately before the call by a
     * {@code requestFocus} method reference, or by a lambda whose body calls a method named {@code requestFocus}.
     */
    private static List<String> deferredFocusRequests(ClassModel owner, List<Instruction> instructions) {
        List<String> found = new ArrayList<>();
        for (int index = 1; index < instructions.size(); index++) {
            if (!(instructions.get(index) instanceof InvokeInstruction invoke)
                    || !invoke.owner().asInternalName().equals(PLATFORM)
                    || !invoke.name().stringValue().equals("runLater")
                    || !(instructions.get(index - 1) instanceof InvokeDynamicInstruction indy))
                continue;
            Optional<CallTarget> implementation = lambdaImplementation(indy);
            if (implementation.isEmpty())
                continue;
            CallTarget target = implementation.orElseThrow();
            if (target.name().equals("requestFocus")) {
                found.add(PLATFORM + ".runLater(" + simpleName(target.owner()) + ".requestFocus)");
            } else if (target.owner().equals(owner.thisClass().asInternalName())
                    && lambdaBodyRequestsFocus(owner, target.name())) {
                found.add(PLATFORM + ".runLater(lambda calling requestFocus)");
            }
        }
        return found;
    }

    /** True when the named synthetic lambda method in {@code owner} directly calls a method named requestFocus. */
    private static boolean lambdaBodyRequestsFocus(ClassModel owner, String lambdaName) {
        for (MethodModel method : owner.methods()) {
            if (!method.methodName().stringValue().equals(lambdaName) || method.code().isEmpty())
                continue;
            return callTargets(instructions(method.code().orElseThrow())).stream()
                    .anyMatch(call -> call.name().equals("requestFocus"));
        }
        return false;
    }

    /** {@code javafx/scene/Node} becomes {@code Node}. */
    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    /** Parses the emitted class file of a compiled fixture from the test classpath. */
    private static ClassModel fixture(Class<?> type) throws IOException {
        String resource = type.getName().substring(type.getPackageName().length() + 1) + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "missing fixture class file " + resource);
            return ClassFile.of().parse(in.readAllBytes());
        }
    }

    /** Every class file javac emitted into target/classes, parsed. */
    private static List<ClassModel> productionClasses() throws IOException, URISyntaxException {
        Path root = Path.of(ProjectSession.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "production classes must be on the test classpath as a directory: " + root);
        List<ClassModel> classes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList())
                classes.add(ClassFile.of().parse(Files.readAllBytes(file)));
        }
        return classes;
    }

    /** One member named as a call target, by owner internal name and method name. */
    record CallTarget(String owner, String name) {
    }

    /** The class and method that contain a call; {@code method} {@code "*"} allows every method of the class. */
    record MethodKey(String declaringClass, String method) {
        /** True when {@code site} is this key, or any method of this key's class for a {@code "*"} key. */
        boolean matches(MethodKey site) {
            return declaringClass.equals(site.declaringClass()) && (method.equals("*") || method.equals(site.method()));
        }

        @Override
        public String toString() {
            return declaringClass + "." + method;
        }
    }

    /** One reported call: the rule it breaks, the method containing it, and what it calls. */
    private record Violation(String rule, MethodKey site, String detail) {
        @Override
        public String toString() {
            return "rule " + rule + ": " + site + " -> " + detail;
        }
    }

    /** Finds the offending calls in one method body; {@code owner} lets a matcher inspect lambda bodies. */
    @FunctionalInterface
    interface Matcher {
        /** Returns one description per offending call, in instruction order. */
        List<String> find(ClassModel owner, List<Instruction> instructions);
    }

    /**
     * One numbered ban with its matcher and allowlist.
     *
     * @param id the rule number used in docs/build/workbench-platform.md
     * @param summary what the rule forbids, for failure messages
     * @param matcher finds offending calls in a method body
     * @param allowed declaring class + method keys whose calls are permitted, each with a why-comment at its site
     */
    record Rule(String id, String summary, Matcher matcher, Set<MethodKey> allowed) {
        Rule {
            allowed = Set.copyOf(allowed);
        }

        /** A rule that bans any call target (invocation or method reference) matching {@code banned}. */
        static Rule invocations(String id, String summary, Predicate<CallTarget> banned, Set<MethodKey> allowed) {
            return new Rule(id, summary, (owner, instructions) -> callTargets(instructions).stream()
                    .filter(banned)
                    .map(call -> call.owner() + "." + call.name())
                    .toList(), allowed);
        }

        /** Rule 4: bans deferred focus requests through {@code Platform.runLater}. */
        static Rule deferredFocus(String id, String summary, Set<MethodKey> allowed) {
            return new Rule(id, summary, ProductionPlatformBanGateTest::deferredFocusRequests, allowed);
        }

        /** The same rule with a different allowlist, for proving the allowlist mechanism. */
        Rule withAllowed(Set<MethodKey> replacement) {
            return new Rule(id, summary, matcher, replacement);
        }

        /** True when a call inside {@code site} is permitted by this rule's allowlist. */
        boolean allows(MethodKey site) {
            return allowed.stream().anyMatch(key -> key.matches(site));
        }
    }
}
