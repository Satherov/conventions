package dev.satherov.conventions.javac.rule.layout;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;
import dev.satherov.conventions.javac.source.SourceSet;

import com.sun.source.tree.ImportTree;
import com.sun.source.util.TreePath;

import java.util.List;

///
/// The rule that imports are single types in the fixed group order, one blank line between groups, and that main
/// code has no static imports.
///
/// The groups are Lombok, the project's own packages, the mod loader, Minecraft, other libraries, `org.*`, the JDK,
/// and static imports. A nested type is imported through its outer type, never on its own. The order inside a group
/// is left to the formatter. Inside the library group, one blank line may set apart the imports of two vendors.
///
public class ImportsRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "imports";

    ///
    /// The names of the import groups, in their order.
    ///
    private static final List<String> GROUPS = List.of(
            "lombok",
            "project",
            "mod loader",
            "minecraft",
            "library",
            "org",
            "jdk",
            "static"
    );

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return ImportsRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks every import of the file and the spacing between them.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        ImportTree previous = null;
        for (ImportTree importTree : file.unit().getImports()) {
            String name = importTree.getQualifiedIdentifier().toString();
            if (name.endsWith(".*")) {
                findings.report(ImportsRule.ID, file, importTree, "a wildcard import; import each type");
            }

            if (importTree.isStatic() && file.options().set() == SourceSet.MAIN) {
                findings.report(ImportsRule.ID, file, importTree, "a static import in main code; qualify the call with its type");
            }

            if (!importTree.isStatic() && ImportsRule.isNested(name)) {
                findings.report(ImportsRule.ID, file, importTree, name + " is a nested type; import its outer type and write Outer.Inner");
            }

            if (previous != null) {
                ImportsRule.checkOrder(file, previous, importTree, findings);
            }

            previous = importTree;
        }
    }

    ///
    /// Checks if the given import points at a type nested in another type, which shows as two capitalized parts.
    ///
    private static boolean isNested(String name) {
        String[] parts = name.split("\\.");
        int capitalized = 0;
        for (String part : parts) {
            if (!part.isEmpty() && Character.isUpperCase(part.charAt(0))) {
                capitalized++;
            }
        }

        return capitalized >= 2;
    }

    ///
    /// Records a breach if the given import comes before the group of the previous one, or if the spacing between
    /// them does not match their groups.
    ///
    private static void checkOrder(SourceFile file, ImportTree previous, ImportTree current, Findings findings) {
        int before = ImportsRule.groupOf(file, previous);
        int group = ImportsRule.groupOf(file, current);
        long gap = file.line(file.start(current)) - file.line(file.end(previous)) - 1;
        if (group < before) {
            findings.report(ImportsRule.ID, file, current, "a " + ImportsRule.GROUPS.get(group) + " import after a " + ImportsRule.GROUPS.get(before) + " import; the groups go " + String.join(", ", ImportsRule.GROUPS));
        } else if (group == before && gap != 0 && !ImportsRule.isVendorBreak(group, previous, current, gap)) {
            findings.report(ImportsRule.ID, file, current, "a blank line inside the " + ImportsRule.GROUPS.get(group) + " import group");
        } else if (group > before && gap != 1) {
            findings.report(ImportsRule.ID, file, current, "the " + ImportsRule.GROUPS.get(group) + " import group is set off by exactly one blank line");
        }
    }

    ///
    /// Checks if the given imports of the library group are set apart by one blank line because they come from
    /// different vendors, such as `dev.satherov.zelqro` and `io.micrometer`.
    ///
    private static boolean isVendorBreak(int group, ImportTree previous, ImportTree current, long gap) {
        return group == ImportsRule.GROUPS.indexOf("library") &&
                gap == 1 &&
                !ImportsRule.vendorOf(previous).equals(ImportsRule.vendorOf(current));
    }

    ///
    /// Gets the first two parts of the name the given import brings in, which name its vendor.
    ///
    private static String vendorOf(ImportTree importTree) {
        String[] parts = importTree.getQualifiedIdentifier()
                .toString()
                .split("\\.");
        return parts[0] + "." + parts[1];
    }

    ///
    /// Gets the index of the group the given import belongs to.
    ///
    private static int groupOf(SourceFile file, ImportTree importTree) {
        if (importTree.isStatic()) {
            return ImportsRule.GROUPS.indexOf("static");
        }

        String name = importTree.getQualifiedIdentifier().toString();
        String root = file.options().root();
        if (name.startsWith("lombok.")) {
            return ImportsRule.GROUPS.indexOf("lombok");
        }

        if (!root.isEmpty() && name.startsWith(root + ".")) {
            return ImportsRule.GROUPS.indexOf("project");
        }

        if (name.startsWith("net.neoforged.") || name.startsWith("net.fabricmc.")) {
            return ImportsRule.GROUPS.indexOf("mod loader");
        }

        if (name.startsWith("net.minecraft.")) {
            return ImportsRule.GROUPS.indexOf("minecraft");
        }

        if (name.startsWith("org.")) {
            return ImportsRule.GROUPS.indexOf("org");
        }

        if (name.startsWith("java.") || name.startsWith("javax.")) {
            return ImportsRule.GROUPS.indexOf("jdk");
        }

        return ImportsRule.GROUPS.indexOf("library");
    }
}
