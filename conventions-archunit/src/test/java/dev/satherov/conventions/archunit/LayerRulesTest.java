package dev.satherov.conventions.archunit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about packages and layers.
///
class LayerRulesTest {

    ///
    /// An internal type the api fixtures expose.
    ///
    private static final String SECRET = """
            package dev.example.internal.core;

            public class Secret { }
            """;

    @Test
    void apiExposesNoInternalTypes() {
        List<String> violations = new Fixtures()
                .main(LayerRulesTest.SECRET, """
                        package dev.example.api.core;

                        import dev.example.internal.core.Secret;

                        import java.util.List;
                        import java.util.function.Supplier;

                        public class Leaky extends Secret implements Supplier<Secret> {

                            public Secret field;

                            private Secret hidden;

                            public List<Secret> all() {
                                return List.of();
                            }

                            public <VALUE extends Secret> void take(VALUE value) { }

                            @Override
                            public Secret get() {
                                return this.hidden;
                            }
                        }
                        """)
                .violationsOf(LayerRules.API_EXPOSES_NO_INTERNAL_TYPES);

        assertThat(violations)
                .hasSize(7)
                .allMatch(violation -> violation.contains("exposes the internal type dev.example.internal.core.Secret"))
                .noneMatch(violation -> violation.contains("hidden"));
    }

    @Test
    void apiMayUseInternalTypesInside() {
        List<String> violations = new Fixtures()
                .main(LayerRulesTest.SECRET, """
                        package dev.example.api.core;

                        import dev.example.internal.core.Secret;

                        public class Wrapper {

                            private final Secret secret = new Secret();

                            public String name() {
                                return this.secret.toString();
                            }
                        }
                        """)
                .violationsOf(LayerRules.API_EXPOSES_NO_INTERNAL_TYPES);

        assertThat(violations).isEmpty();
    }

    @Test
    void layerRootsHoldNoTypes() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.api;

                        public interface Loose { }
                        """, """
                        package dev.example.api.core;

                        public interface Placed { }
                        """)
                .violationsOf(LayerRules.LAYER_ROOTS_HOLD_NO_TYPES);

        assertThat(violations)
                .hasSize(1)
                .allMatch(violation -> violation.contains("dev.example.api.Loose"));
    }

    @Test
    void noKindPackages() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.internal.util;

                        public class Strings { }
                        """, """
                        package dev.example.api.network.impl;

                        public class Socket { }
                        """, """
                        package dev.example.impl.network;

                        public class Socket { }
                        """)
                .violationsOf(LayerRules.NO_KIND_PACKAGES);

        assertThat(violations)
                .hasSize(2)
                .anyMatch(violation -> violation.contains("kind package util"))
                .anyMatch(violation -> violation.contains("impl package inside the api layer"));
    }
}
