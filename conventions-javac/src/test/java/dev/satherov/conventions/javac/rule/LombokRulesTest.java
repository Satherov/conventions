package dev.satherov.conventions.javac.rule;

import dev.satherov.conventions.javac.Javac;
import dev.satherov.conventions.javac.rule.lombok.GetterPlacementRule;
import dev.satherov.conventions.javac.rule.lombok.LombokFormRule;
import dev.satherov.conventions.javac.rule.lombok.UtilityClassRule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about Lombok.
///
class LombokRulesTest {

    @Test
    void getterPlacementWantsOneGetterOnTheTypeWhenEveryFieldHasOne() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import lombok.Getter;
                import lombok.experimental.Accessors;

                @Accessors(fluent = true)
                class Sample {

                    @Getter
                    private int count;

                    @Getter
                    private String name;
                }
                """);

        assertThat(report.getAllOf(GetterPlacementRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly("every field of Sample has @Getter; put one @Getter on the type");
    }

    @Test
    void getterPlacementFlagsFieldGettersUnderATypeGetter() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import lombok.Getter;
                import lombok.experimental.Accessors;

                @Getter
                @Accessors(fluent = true)
                class Sample {

                    @Getter
                    private int count;

                    @Getter(lazy = true)
                    private final String name = "";

                    private static int total;
                }
                """);

        assertThat(report.getAllLinesOf(GetterPlacementRule.ID)).containsExactly(11L);
    }

    @Test
    void getterPlacementAcceptsSomeFieldsWithGettersAndWantsFluentAccessors() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import lombok.Getter;

                class Sample {

                    @Getter
                    private int count;

                    private String name = "";
                }
                """);

        assertThat(report.getAllOf(GetterPlacementRule.ID))
                .extracting(Javac.Finding::message)
                .containsExactly("Sample has getters but no @Accessors(fluent = true)");
    }

    @Test
    void utilityClassReplacesAHandWrittenPrivateConstructor() {
        Javac.Report report = Javac.main("""
                package dev.example;

                class Sample {

                    static final int MAX = 1;

                    private Sample() { }

                    static int twice(int value) {
                        return value * 2;
                    }
                }
                """, """
                package dev.example;

                class Instance {

                    private Instance() { }

                    int count() {
                        return 0;
                    }
                }
                """);

        assertThat(report.getAllOf(UtilityClassRule.ID))
                .extracting(Javac.Finding::file)
                .containsExactly("dev/example/Sample.java");
    }

    @Test
    void lombokFormFlagsOldForms() {
        Javac.Report report = Javac.main("""
                package dev.example;

                import lombok.Getter;
                import lombok.NonNull;

                class Sample {

                    @Getter(onMethod_ = {@Deprecated})
                    private int first;

                    @Getter(onMethod_ = @Deprecated)
                    private int second;

                    void take(@NonNull String text) { }
                }
                """);

        assertThat(report.getAllLinesOf(LombokFormRule.ID)).containsExactly(4L, 8L);
    }
}
