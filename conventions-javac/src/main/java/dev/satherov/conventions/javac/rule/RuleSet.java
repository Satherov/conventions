package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.rule.annotation.AnnotationLineRule;
import dev.satherov.conventions.javac.rule.annotation.DeprecatedRule;
import dev.satherov.conventions.javac.rule.annotation.GeneratedRule;
import dev.satherov.conventions.javac.rule.annotation.InternalOnceRule;
import dev.satherov.conventions.javac.rule.annotation.OverrideRule;
import dev.satherov.conventions.javac.rule.annotation.PackageInfoRule;
import dev.satherov.conventions.javac.rule.annotation.SuppressWarningsRule;
import dev.satherov.conventions.javac.rule.annotation.UnmodifiableRule;
import dev.satherov.conventions.javac.rule.comment.CommentedCodeRule;
import dev.satherov.conventions.javac.rule.comment.DocFormRule;
import dev.satherov.conventions.javac.rule.comment.DocPresenceRule;
import dev.satherov.conventions.javac.rule.comment.NoinspectionReasonRule;
import dev.satherov.conventions.javac.rule.declaration.BitConstantRule;
import dev.satherov.conventions.javac.rule.declaration.TypeParameterRule;
import dev.satherov.conventions.javac.rule.declaration.VisibilityRule;
import dev.satherov.conventions.javac.rule.expression.IdentityLambdaRule;
import dev.satherov.conventions.javac.rule.expression.LocaleRule;
import dev.satherov.conventions.javac.rule.expression.NestedComputeRule;
import dev.satherov.conventions.javac.rule.expression.NullTernaryRule;
import dev.satherov.conventions.javac.rule.expression.ObjectUnderscoreRule;
import dev.satherov.conventions.javac.rule.expression.QualificationRule;
import dev.satherov.conventions.javac.rule.layout.BlockSpacingRule;
import dev.satherov.conventions.javac.rule.layout.ClassicSwitchRule;
import dev.satherov.conventions.javac.rule.layout.EmptyBodyRule;
import dev.satherov.conventions.javac.rule.layout.FormatterGuardRule;
import dev.satherov.conventions.javac.rule.layout.IfBracesRule;
import dev.satherov.conventions.javac.rule.layout.ImportsRule;
import dev.satherov.conventions.javac.rule.layout.MemberOrderRule;
import dev.satherov.conventions.javac.rule.layout.NumericSuffixRule;
import dev.satherov.conventions.javac.rule.layout.OneTypeRule;
import dev.satherov.conventions.javac.rule.lombok.GetterPlacementRule;
import dev.satherov.conventions.javac.rule.lombok.LombokFormRule;
import dev.satherov.conventions.javac.rule.lombok.UtilityClassRule;
import dev.satherov.conventions.javac.source.Hierarchy;
import dev.satherov.conventions.javac.source.Options;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

///
/// The rules one compilation runs, every rule but the ones the options disable.
///
/// @param rules The rules, in the order they run.
///
public record RuleSet(@Unmodifiable List<Rule> rules) {

    ///
    /// Copies the rules, so that the set cannot change afterwards.
    ///
    public RuleSet {
        rules = List.copyOf(rules);
    }

    ///
    /// Creates every rule for the given task and drops the ones the options disable.
    ///
    /// @param task    The compilation the rules run in.
    /// @param options The options the plugin runs with.
    ///
    /// @return The rules that run.
    ///
    /// @throws IllegalArgumentException If the options disable an id no rule has.
    ///
    public static RuleSet from(JavacTask task, Options options) {
        List<Rule> all = RuleSet.create(task);
        Set<String> ids = all.stream()
                .map(Rule::id)
                .collect(Collectors.toSet());
        List<String> unknown = options.disabled()
                .stream()
                .filter(id -> !ids.contains(id))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown conventions rules to disable: " + String.join(", ", unknown));
        }

        return new RuleSet(all.stream()
                .filter(rule -> !options.disabled().contains(rule.id()))
                .toList());
    }

    ///
    /// Creates one of every rule.
    ///
    private static @Unmodifiable List<Rule> create(JavacTask task) {
        Trees trees = Trees.instance(task);
        Hierarchy hierarchy = new Hierarchy(task.getElements(), task.getTypes());
        List<Rule> rules = List.of(
                new DocFormRule(),
                new DocPresenceRule(),
                new NoinspectionReasonRule(),
                new CommentedCodeRule(),
                new InternalOnceRule(),
                new AnnotationLineRule(),
                new DeprecatedRule(),
                new GeneratedRule(),
                new PackageInfoRule(),
                new GetterPlacementRule(),
                new UtilityClassRule(),
                new LombokFormRule(),
                new EmptyBodyRule(),
                new IfBracesRule(),
                new BlockSpacingRule(),
                new ClassicSwitchRule(),
                new NumericSuffixRule(),
                new MemberOrderRule(),
                new OneTypeRule(),
                new FormatterGuardRule(),
                new ImportsRule(),
                new NullTernaryRule(),
                new IdentityLambdaRule(),
                new ObjectUnderscoreRule(),
                new VisibilityRule(),
                new TypeParameterRule(),
                new OverrideRule(trees, hierarchy),
                new UnmodifiableRule(trees, task.getElements(), task.getTypes(), hierarchy),
                new LocaleRule(trees),
                new QualificationRule(trees, task.getTypes()),
                new BitConstantRule(trees),
                new NestedComputeRule(trees, task.getElements(), task.getTypes())
        );
        Set<String> ids = Stream.concat(rules.stream().map(Rule::id), Stream.of(SuppressWarningsRule.ID))
                .collect(Collectors.toUnmodifiableSet());
        return Stream.concat(rules.stream(), Stream.of(new SuppressWarningsRule(ids)))
                .toList();
    }

    ///
    /// Gets the rules that run at the given phase, in order.
    ///
    /// @param phase The phase.
    ///
    /// @return Any rules of the phase.
    ///
    public @Unmodifiable List<Rule> getAllIn(Phase phase) {
        return this.rules.stream()
                .filter(rule -> rule.phase() == phase)
                .toList();
    }
}
