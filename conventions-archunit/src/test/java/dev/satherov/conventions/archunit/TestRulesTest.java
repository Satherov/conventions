package dev.satherov.conventions.archunit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests the rules about test code.
///
class TestRulesTest {

    @Test
    void testsMirrorMain() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.api.user;

                        public class User { }
                        """, """
                        package dev.example.api.guild;

                        public class Guild { }
                        """)
                .test("""
                        package dev.example.api.user;

                        class UserTest { }
                        """, """
                        package dev.example.api.user;

                        class GuildTest { }
                        """, """
                        package dev.example.api.user;

                        class WorkflowTest { }
                        """)
                .violationsOf(TestRules.TESTS_MIRROR_MAIN);

        assertThat(violations)
                .hasSize(1)
                .allMatch(violation -> violation.contains("GuildTest tests a type in dev.example.api.guild"));
    }

    @Test
    void mainCodeIsNotTest() {
        List<String> violations = new Fixtures()
                .main("""
                        package dev.example.api.user;

                        public class LoadTest { }
                        """)
                .violationsOf(TestRules.TESTS_MIRROR_MAIN);

        assertThat(violations).isEmpty();
    }
}
