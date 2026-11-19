package dev.satherov.conventions.javac.rule.comment;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.Line;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.util.TreePath;

import java.util.ArrayList;
import java.util.List;

///
/// The rule that doc comments are `///` lines framed by a bare `///` above and below, with markdown links.
///
public class DocFormRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "doc-form";

    ///
    /// The marker every line of a doc comment starts with.
    ///
    private static final String MARKER = "///";

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return DocFormRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every doc comment block and every `/**` opener of the file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        List<Line> block = new ArrayList<>();
        for (Line line : file.getAllLines()) {
            if (file.isInLiteral(line.contentStart())) {
                continue;
            }

            if (line.content().startsWith("/**")) {
                findings.report(DocFormRule.ID, file, line, "a /** */ comment; doc comments are /// lines");
            }

            if (line.content().startsWith(DocFormRule.MARKER)) {
                block.add(line);
                DocFormRule.checkMarkup(file, line, findings);
            } else if (!block.isEmpty()) {
                DocFormRule.checkFrame(file, block, findings);
                block.clear();
            }
        }

        if (!block.isEmpty()) {
            DocFormRule.checkFrame(file, block, findings);
        }
    }

    ///
    /// Records a breach for every javadoc inline tag on the given doc line.
    ///
    private static void checkMarkup(SourceFile file, Line line, Findings findings) {
        if (line.content().contains("{@link")) {
            findings.report(DocFormRule.ID, file, line, "{@link}; write a markdown link such as [Type#member()]");
        }

        if (line.content().contains("{@inheritDoc}")) {
            findings.report(DocFormRule.ID, file, line, "{@inheritDoc}; an override takes one line of its own");
        }
    }

    ///
    /// Records a breach if the given doc comment block does not open and close with a bare `///`.
    ///
    private static void checkFrame(SourceFile file, List<Line> block, Findings findings) {
        if (!block.getFirst().content().equals(DocFormRule.MARKER)) {
            findings.report(DocFormRule.ID, file, block.getFirst(), "a doc comment opens with a bare ///");
        }

        if (block.size() == 1 || !block.getLast().content().equals(DocFormRule.MARKER)) {
            findings.report(DocFormRule.ID, file, block.getLast(), "a doc comment closes with a bare ///");
        }
    }
}
