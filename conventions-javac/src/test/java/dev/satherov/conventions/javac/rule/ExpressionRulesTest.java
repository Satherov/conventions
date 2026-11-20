package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.expression.IdentityLambdaRule;
import dev.satherov.conventions.javac.rule.expression.LocaleRule;
import dev.satherov.conventions.javac.rule.expression.NestedComputeRule;
import dev.satherov.conventions.javac.rule.expression.NullTernaryRule;
import dev.satherov.conventions.javac.rule.expression.ObjectUnderscoreRule;
import dev.satherov.conventions.javac.rule.expression.QualificationRule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

///
/// Tests the rules about expressions.
///
class ExpressionRulesTest {

    @Test
    void qualificationFlagsBareMembers() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    static final int MAX = 1;

                    int count;

                    int run() {
                        count = MAX;
                        return size();
                    }

                    int size() {
                        return this.count + Sample.MAX;
                    }
                }
                """);

        assertThat(report.getAllOf(QualificationRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly(
                        "count is unqualified; write this.count",
                        "MAX is unqualified; write Sample.MAX",
                        "size is unqualified; write this.size"
                );
    }

    @Test
    void qualificationFlagsInheritedMembersAndLeavesOthersAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.ArrayList;

                import static java.lang.Math.max;

                class Sample extends ArrayList<String> {

                    enum Color {
                        RED,
                        BLUE
                    }

                    int run(Color color, int other) {
                        int local = max(other, 1);
                        int width = switch (color) {
                            case RED -> 1;
                            case BLUE -> 2;
                        };
                        return local + width + size();
                    }

                    @SuppressWarnings(value = "unchecked")
                    Sample self() {
                        return this;
                    }

                    Sample() {
                        super();
                    }
                }
                """);

        assertThat(report.getAllOf(QualificationRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly("size is unqualified; write this.size");
    }

    @Test
    void qualificationLeavesLombokMethodsAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import lombok.Data;

                @Data
                class Sample {

                    private int count;
                }
                """);

        assertThat(report.getAllOf(QualificationRule.ID)).isEmpty();
    }

    @Test
    void localeIsRequired() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.Locale;

                class Sample {

                    void run(String text) {
                        text.toUpperCase();
                        text.toLowerCase(Locale.ROOT);
                        String.format("%d", 1);
                        String.format(Locale.ROOT, "%d", 1);
                        "%d".formatted(1);
                    }
                }
                """);

        assertThat(report.getAllLinesOf(LocaleRule.ID)).containsExactly(8L, 10L, 12L);
    }

    @Test
    void nullTernaryIsFlagged() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    String run(String text, String other, boolean ready) {
                        String first = text == null ? "" : text.strip();
                        String second = (null != text) ? text : "";
                        String third = text == null ? "none" : "some";
                        String fourth = text != null ? other.strip() : text;
                        String fifth = other == null ? text : other;
                        return ready ? first + third + fourth : second + fifth;
                    }
                }
                """);

        assertThat(report.getAllLinesOf(NullTernaryRule.ID)).containsExactly(6L, 7L, 10L);
    }

    @Test
    void identityLambdaIsFlagged() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.function.Function;

                class Sample {

                    Function<String, String> same = text -> text;

                    Function<String, String> other = text -> text.strip();
                }
                """);

        assertThat(report.getAllLinesOf(IdentityLambdaRule.ID)).containsExactly(7L);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_22)
    void objectUnderscoreIsFlagged() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    void run(StringBuilder builder) {
                        Object _ = builder.append("x");
                    }
                }
                """);

        assertThat(report.getAllLinesOf(ObjectUnderscoreRule.ID)).containsExactly(6L);
    }

    @Test
    void nestedComputeFlagsWritesToOtherMapsAndCaches() {
        Javac.Report report = Javac.main("""
                package com.github.benmanes.caffeine.cache;

                public interface Cache<KEY, VALUE> {

                    void put(KEY key, VALUE value);

                    void invalidate(KEY key);

                    VALUE getIfPresent(KEY key);
                }
                """, """
                package dev.example;

                import com.github.benmanes.caffeine.cache.Cache;

                import java.util.ArrayDeque;
                import java.util.Deque;
                import java.util.Map;
                import java.util.concurrent.ConcurrentHashMap;

                class Rings {

                    private final Map<String, Deque<String>> rings = new ConcurrentHashMap<>();

                    private final Map<String, String> owners = new ConcurrentHashMap<>();

                    private final Cache<String, String> store;

                    Rings(Cache<String, String> store) {
                        this.store = store;
                    }

                    void note(String channel, String message) {
                        this.rings.compute(channel, (key, ring) -> {
                            Deque<String> ids = ring == null ? new ArrayDeque<>() : ring;
                            ids.addLast(message);
                            this.store.invalidate(ids.removeFirst());
                            this.owners.remove(message);
                            this.rings.remove("other");
                            return ids;
                        });
                        this.owners.merge(channel, message, (left, right) -> {
                            this.trim(right);
                            return left;
                        });
                    }

                    private void trim(String message) {
                        this.store.put(message, message);
                    }
                }
                """);

        assertThat(report.getAllOf(NestedComputeRule.ID))
                .extracting(Javac.Finding::file, Javac.Finding::line)
                .containsExactly(
                        tuple("dev/example/Rings.java", 26L),
                        tuple("dev/example/Rings.java", 27L),
                        tuple("dev/example/Rings.java", 32L)
                );
    }

    @Test
    void nestedComputeFlagsWritesToFieldsOfAnyType() {
        Javac.Report report = Javac.main("""
                package dev.example;

                interface Store {

                    void remove(String key);
                }
                """, """
                package dev.example;

                import java.util.ArrayDeque;
                import java.util.Deque;
                import java.util.Map;
                import java.util.concurrent.ConcurrentHashMap;

                class Messages {

                    private final Map<String, Deque<String>> rings = new ConcurrentHashMap<>();

                    private final Store store;

                    Messages(Store store) {
                        this.store = store;
                    }

                    void note(String channel, String message) {
                        this.rings.compute(channel, (key, ring) -> {
                            Deque<String> ids = ring == null ? new ArrayDeque<>() : ring;
                            ids.addLast(message);
                            while (ids.size() > 10) {
                                this.store.remove(ids.removeFirst());
                            }

                            return ids;
                        });
                    }

                    class Trimmer {

                        void trim(String channel) {
                            Messages.this.rings.computeIfPresent(channel, (key, ids) -> {
                                Messages.this.store.remove(key);
                                ids.remove(key);
                                return ids;
                            });
                        }
                    }
                }
                """);

        assertThat(report.getAllOf(NestedComputeRule.ID))
                .extracting(Javac.Finding::file, Javac.Finding::line)
                .containsExactly(
                        tuple("dev/example/Messages.java", 23L),
                        tuple("dev/example/Messages.java", 34L)
                );
    }

    @Test
    void nestedComputeLeavesTheOwnValueAndOtherCallsAlone() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import java.util.HashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.concurrent.ConcurrentHashMap;

                class Groups {

                    private final Map<String, Map<String, String>> groups = new ConcurrentHashMap<>();

                    private final Map<String, String> names = new ConcurrentHashMap<>();

                    void add(String group, String key, List<String> log) {
                        this.groups.computeIfAbsent(group, _key -> new HashMap<>()).put(key, key);
                        this.groups.computeIfPresent(group, (_key, members) -> {
                            members.put(key, key);
                            Map<String, String> copy = new HashMap<>(members);
                            copy.remove(key);
                            log.remove(key);
                            this.fill(members);
                            return copy;
                        });
                        this.names.put(group, key);
                    }

                    private void fill(Map<String, String> members) {
                        members.clear();
                    }
                }
                """);

        assertThat(report.getAllOf(NestedComputeRule.ID)).isEmpty();
    }
}
