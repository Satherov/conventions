# conventions

The code conventions of Satherov's Java projects, as checks that fail the build.

- `conventions-javac`: a javac plugin. It reads the source as javac parses and attributes it, so it sees what the class
  file drops: doc comments, imports, Lombok annotations, `@Override`, type-use annotations. Every finding is a compile
  error, reported once the compilation ends.
- `conventions-archunit`: ArchUnit rules over the compiled classes: packages and layers, type names, type shapes,
  and where tests sit.
- `conventions-gradle`: the Gradle plugin `dev.satherov.conventions`, which starts the javac plugin in every compile
  task and adds the ArchUnit rules to the tests.

## Use

```kotlin
plugins {
    id("dev.satherov.conventions") version "1.0.1"
}

conventions {
    rootPackage = "dev.satherov.example"
}
```

The architecture test pulls in the global rules and adds the project's own:

```java

@AnalyzeClasses(packages = "dev.satherov.example")
class ArchitectureTest {
    
    @ArchTest
    static final ArchTests GLOBAL = ArchTests.in(GlobalRules.class);
}
```

## Options

| Option        | Effect                                                                                                    |
|---------------|-----------------------------------------------------------------------------------------------------------|
| `rootPackage` | The project's own import group.                                                                           |
| `disabled`    | Ids of javac rules that do not run, as printed in `[conventions:<id>]`.                                   |
| `generated`   | Directories of sources the project generates. Source directories below the build directory count already. |

## Suppression

A finding of a javac rule is suppressed on the method, constructor, or type around it, with the rule's id as printed
in `[conventions:<id>]`:

```java
@SuppressWarnings("conventions:unmodifiable")
private Deque<Request> queue(Lane lane) {
    return this.queues.get(lane);
}
```

A `@SuppressWarnings` on a field, a parameter, a record component, or a local is a finding of its own. A finding of an
ArchUnit rule is excused in `archunit_ignore_patterns.txt` on the test classpath.

## Build

```fish
./gradlew build
```

The checks compile for Java 21, so projects on Java 21 and later can use them.
