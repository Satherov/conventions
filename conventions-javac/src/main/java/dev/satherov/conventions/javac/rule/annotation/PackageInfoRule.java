package dev.satherov.conventions.javac.rule.annotation;

import dev.satherov.conventions.javac.rule.Phase;
import dev.satherov.conventions.javac.rule.Rule;
import dev.satherov.conventions.javac.source.Annotations;
import dev.satherov.conventions.javac.source.Findings;
import dev.satherov.conventions.javac.source.SourceFile;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.util.TreePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

///
/// The rule that every package has a `package-info.java` holding its doc comment, `@NullMarked` and
/// `@CheckReturnValue`, and no other annotation.
///
/// A package counts as covered if its `package-info.java` is compiled with it or lies next to one of its files on
/// disk, so that an incremental compilation that leaves the file out does not report it as missing.
///
public class PackageInfoRule implements Rule {

    ///
    /// The id of the rule.
    ///
    public static final String ID = "package-info";

    ///
    /// The file name of a package declaration file.
    ///
    private static final String FILE_NAME = "package-info.java";

    ///
    /// The simple names of the annotations every package carries, and the only ones it may carry.
    ///
    public static final Set<String> ANNOTATIONS = Set.of(
            "NullMarked",
            "CheckReturnValue"
    );

    ///
    /// The first file seen of each package, which a missing `package-info.java` is reported on.
    ///
    private final Map<String, SourceFile> firstFileByPackage = new LinkedHashMap<>();

    ///
    /// The packages known to have a `package-info.java`.
    ///
    private final Set<String> covered = new HashSet<>();

    ///
    /// The id [#ID].
    ///
    @Override
    public String id() {
        return PackageInfoRule.ID;
    }

    ///
    /// The parsed phase.
    ///
    @Override
    public Phase phase() {
        return Phase.PARSED;
    }

    ///
    /// Checks the shape of a `package-info.java`, and notes the package of any other file.
    ///
    @Override
    public void check(SourceFile file, TreePath path, Findings findings) {
        PackageTree declaration = file.unit().getPackage();
        ExpressionTree name = file.unit().getPackageName();
        if (declaration == null || name == null) {
            return;
        }

        String packageName = name.toString();
        if (file.isPackageInfo()) {
            this.covered.add(packageName);
            PackageInfoRule.checkShape(file, declaration, findings);
            return;
        }

        this.firstFileByPackage.putIfAbsent(packageName, file);
        boolean onDisk = file.path()
                .map(Path::getParent)
                .map(directory -> Files.exists(directory.resolve(PackageInfoRule.FILE_NAME)))
                .orElse(false);
        if (onDisk) {
            this.covered.add(packageName);
        }
    }

    ///
    /// Records a breach for every package seen without a `package-info.java`.
    ///
    @Override
    public void finish(Findings findings) {
        this.firstFileByPackage.forEach((packageName, file) -> {
            if (!this.covered.contains(packageName)) {
                findings.report(PackageInfoRule.ID, file, file.unit().getPackage(), "package " + packageName + " has no " + PackageInfoRule.FILE_NAME);
            }
        });
    }

    ///
    /// Records a breach for every annotation the package lacks or carries beyond the two, and for a missing doc comment.
    ///
    private static void checkShape(SourceFile file, PackageTree declaration, Findings findings) {
        List<? extends AnnotationTree> annotations = declaration.getAnnotations();
        Set<String> names = new HashSet<>();
        for (AnnotationTree annotation : annotations) {
            String simpleName = Annotations.simpleName(annotation);
            names.add(simpleName);
            if (!PackageInfoRule.ANNOTATIONS.contains(simpleName)) {
                findings.report(PackageInfoRule.ID, file, annotation, "@" + simpleName + " on a package; a package carries only @NullMarked and @CheckReturnValue");
            }
        }

        for (String required : PackageInfoRule.ANNOTATIONS) {
            if (!names.contains(required)) {
                findings.report(PackageInfoRule.ID, file, declaration, "package has no @" + required);
            }
        }

        long start = annotations.isEmpty() ? file.start(declaration) : Math.min(file.start(annotations.getFirst()), file.start(declaration));
        if (!file.lineText(file.line(start) - 1).strip().equals("///")) {
            findings.report(PackageInfoRule.ID, file, declaration, "package has no doc comment");
        }
    }
}
