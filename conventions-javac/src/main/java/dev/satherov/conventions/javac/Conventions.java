package dev.satherov.conventions.javac;

import lombok.RequiredArgsConstructor;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.rule.RuleSet;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.Options;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import java.util.IdentityHashMap;
import java.util.Map;

///
/// The javac plugin that checks the code conventions, started with `-Xplugin:Conventions` and the options [Options]
/// reads.
///
/// Every breach is reported as an error once the compilation ends, so that one breach does not stop javac before the
/// rules that need resolved types have run.
///
public final class Conventions implements Plugin {

    ///
    /// The name the plugin is started by.
    ///
    public static final String NAME = "Conventions";

    ///
    /// The name [#NAME].
    ///
    @Override
    public String getName() {
        return Conventions.NAME;
    }

    ///
    /// Reads the options, creates the rules and listens to the compilation.
    ///
    /// @throws IllegalArgumentException If the options are malformed or disable an unknown rule.
    ///
    @Override
    public void init(JavacTask task, String... args) {
        Options options = Options.from(args);
        Trees trees = Trees.instance(task);
        task.addTaskListener(new Listener(options, trees, RuleSet.from(task, options), new Findings(trees)));
    }

    ///
    /// Runs the rules as javac parses and analyzes each file, and reports their findings at the end.
    ///
    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    @RequiredArgsConstructor
    private static final class Listener implements TaskListener {

        ///
        /// The options the plugin runs with.
        ///
        private final Options options;

        ///
        /// The javac trees of the compilation.
        ///
        private final Trees trees;

        ///
        /// The rules that run.
        ///
        private final RuleSet rules;

        ///
        /// The findings of every rule.
        ///
        private final Findings findings;

        ///
        /// The files read so far, by their unit.
        ///
        private final Map<CompilationUnitTree, SourceFile> files = new IdentityHashMap<>();

        ///
        /// Runs the parsed rules once a file is parsed, the analyzed rules once a type is analyzed, and reports every
        /// finding once the compilation ends.
        ///
        @Override
        public void finished(TaskEvent event) {
            switch (event.getKind()) {
                case PARSE -> this.parsed(event.getCompilationUnit());
                case ANALYZE -> this.analyzed(event);
                case COMPILATION -> this.compiled();
                default -> { }
            }
        }

        ///
        /// Reads the parsed unit and runs the parsed rules on it.
        ///
        private void parsed(CompilationUnitTree unit) {
            SourceFile file = new SourceFile(unit, this.trees, this.options);
            this.files.put(unit, file);
            this.run(Phase.PARSED, file, new TreePath(unit));
        }

        ///
        /// Runs the analyzed rules on the type the event names.
        ///
        private void analyzed(TaskEvent event) {
            SourceFile file = this.files.get(event.getCompilationUnit());
            if (file == null || event.getTypeElement() == null) {
                return;
            }

            TreePath path = this.trees.getPath(event.getTypeElement());
            if (path != null) {
                this.run(Phase.ANALYZED, file, path);
            }
        }

        ///
        /// Runs every rule of the given phase that looks at files of the given file's origin.
        ///
        private void run(Phase phase, SourceFile file, TreePath path) {
            for (Rule rule : this.rules.getAllIn(phase)) {
                if (rule.origin() == file.origin()) {
                    rule.check(file, path, this.findings);
                }
            }
        }

        ///
        /// Lets every rule record what only shows at the end, then reports every finding.
        ///
        private void compiled() {
            for (Rule rule : this.rules.rules()) {
                rule.finish(this.findings);
            }

            this.findings.flush();
        }
    }
}
