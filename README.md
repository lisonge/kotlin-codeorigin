# CodeOrigin for Kotlin

[![Maven Central](https://img.shields.io/maven-central/v/li.songe.codeorigin/codeorigin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe.codeorigin/codeorigin)
[![License](http://img.shields.io/:License-Apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

CodeOrigin is a Kotlin compiler plugin for compile-time source introspection. It provides:

- `nameOf(...)` for rename-safe symbol names.
- `sourceOf(...)` for the original text of an expression.
- `evalSourceOf(...)` for the original text and evaluated value of an expression.
- `declarationSourceOf(...)` for declarations in the same source file.
- `@CallSite` for injecting caller location data into omitted default parameters.

All transformations happen in Kotlin IR and work without runtime reflection. Expressions passed to
`nameOf` and `sourceOf` are type-checked but are not evaluated at runtime. `evalSourceOf` evaluates
its expression exactly once.

```kotlin
import li.songe.codeorigin.CallSite
import li.songe.codeorigin.SourceLocation
import li.songe.codeorigin.declarationSourceOf
import li.songe.codeorigin.evalSourceOf
import li.songe.codeorigin.nameOf
import li.songe.codeorigin.sourceOf

data class User(val displayName: String)

fun log(
    message: String,
    @CallSite location: SourceLocation = SourceLocation(),
) {
    println("${location.file}:${location.line}: $message")
}

fun example(user: User) {
    nameOf(user)                    // "user"
    nameOf(user.displayName)        // "displayName"
    nameOf<User>()                  // "User"
    sourceOf(user.displayName)      // "user.displayName"
    evalSourceOf(user.displayName)  // Pair("user.displayName", user.displayName)
    declarationSourceOf<User>()     // "data class User(val displayName: String)"
    log("loaded")                  // location is injected from this call
}
```

## Modules

| Gradle module                  | Published artifact                    | Purpose                   |
| ------------------------------ | ------------------------------------- | ------------------------- |
| `codeorigin`                   | `li.songe:codeorigin`                 | Multiplatform public API  |
| `codeorigin-compiler-plugin`   | `li.songe:codeorigin-compiler-plugin` | K2 compiler plugin        |
| `codeorigin-gradle-plugin`     | `li.songe:codeorigin-gradle-plugin`   | Kotlin Gradle integration |
| `codeorigin-integration-tests` | not published                         | End-to-end JVM tests      |

The public functions, annotation, and `SourceLocation` type all use the
`li.songe.codeorigin` package. The Gradle plugin ID is also `li.songe.codeorigin`.

## Usage

```toml
# gradle/libs.versions.toml
[versions]
codeorigin = "<version>"

[libraries]
codeorigin = { module = "li.songe:codeorigin", version.ref = "codeorigin" }

[plugins]
codeorigin = { id = "li.songe.codeorigin", version.ref = "codeorigin" }
```

```kotlin
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.codeorigin)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.codeorigin)
    }
}
```

The Gradle plugin deliberately does not add the public dependency. This lets a library choose
whether CodeOrigin belongs in `api`, `implementation`, or a particular KMP source set.

## `nameOf`

The value overload accepts named references:

```kotlin
val user = User("Ada")

nameOf(user)                 // "user"
nameOf(user.displayName)     // "displayName"
nameOf(User::displayName)    // "displayName"
nameOf(::example)            // "example"
```

It also supports objects and enum entries. Compound expressions such as function calls,
arithmetic, indexing, and safe calls are rejected with a compiler error.

The type overload returns the source-level simple type name, including a type alias used at the
call site:

```kotlin
typealias Account = User

nameOf<User>()     // "User"
nameOf<Account>()  // "Account"
```

Backticks are removed from escaped identifiers.

## `sourceOf`

`sourceOf` accepts any valid expression and captures its source text without evaluating it:

```kotlin
var count = 0

sourceOf(count++) // "count++"
check(count == 0)
```

Internal whitespace, comments, and literal spelling are preserved. Leading and trailing
whitespace is removed, and CRLF/CR line endings are normalized to LF. A generated or in-memory
source file that cannot be read produces a compiler error instead of an empty or reconstructed
expression.

`evalSourceOf` captures the same source text but also evaluates the expression exactly once. It
returns a `Pair<String, T>` whose `first` value is the source text and whose `second` value is the
expression result:

```kotlin
var count = 0

val (source, value) = evalSourceOf(count++)
check(source == "count++")
check(value == 0)
check(count == 1)
```

If the expression throws, the exception propagates normally and no pair is returned. Both APIs
type-check their expressions. Use `sourceOf` when the expression must not run, and `evalSourceOf`
when both its value and source text are needed.

## `declarationSourceOf`

`declarationSourceOf<T>()` captures the complete source text of a class, interface, object, enum,
or annotation class declared in the same source file as the call. The reference overload accepts
a function or property reference:

```kotlin
declarationSourceOf<User>()
declarationSourceOf(::example)
declarationSourceOf(User::displayName)
```

The reference expression is not evaluated. Leading KDoc and use-site annotations are included.
Common indentation and leading or trailing whitespace are removed, and line endings are normalized to LF. To keep generated constants correct during
incremental compilation, both overloads require the declaration and call to share a source file.
Declarations from another source file, constructor references, generated declarations without
readable source, and non-reference arguments produce compiler errors. The plugin does not use
cross-file metadata or runtime reflection.

## `@CallSite`

`@CallSite` can annotate an omitted default parameter of type `String`, `Int`, or
`SourceLocation`. Explicit arguments always win.

```kotlin
fun trace(
    message: String,
    @CallSite("{file}:{line}") source: String = "",
    @CallSite("{line}") line: Int = -1,
    @CallSite location: SourceLocation = SourceLocation(),
) {
    println("[$source] $message")
}
```

String formats support:

| Field              | Value                                                      | Example                                  |
| ------------------ | ---------------------------------------------------------- | ---------------------------------------- |
| `{path}`           | Source path relative to the Gradle root project, using `/` | `src/main/kotlin/com/example/Greeter.kt` |
| `{file}`           | File name                                                  | `Greeter.kt`                             |
| `{package}`        | Kotlin package name                                        | `com.example`                            |
| `{name}`           | Nearest source declaration name                            | `greet`                                  |
| `{owner}`          | Source declaration path without the package                | `Greeter.greet`                          |
| `{qualifiedOwner}` | Package plus source declaration path                       | `com.example.Greeter.greet`              |
| `{type}`           | Enclosing class/interface/object path                      | `Greeter`                                |
| `{function}`       | Enclosing function, constructor, or accessor               | `greet`                                  |
| `{line}`           | 1-based line number                                        | `12`                                     |
| `{column}`         | 1-based column number                                      | `9`                                      |
| `{offset}`         | 0-based source character offset                            | `180`                                    |

An empty String format uses `{qualifiedOwner}({file}:{line})`. An Int parameter must use exactly
`{line}`, `{column}`, or `{offset}`. A `SourceLocation` parameter is structured and does not accept
a custom format. Use `{{` and `}}` for literal braces in a String format.

When a function overrides another declaration, the plugin also recognizes `@CallSite` on the
corresponding overridden parameter. The override must retain an effective default value. If
multiple inherited annotations specify different formats, compilation fails with a diagnostic;
an annotation written directly on the overriding parameter takes precedence.

## Current scope

- Kotlin 2.4 compiler plugin with K2 support.
- Kotlin/JVM, JS, Native, and Wasm share the same public API and IR transformation.
- `nameOf`, `sourceOf`, and `declarationSourceOf` become constants in generated IR. `evalSourceOf`
  retains its expression and pairs the evaluated value with a source-text constant. These
  intrinsics are not accepted where the Kotlin frontend requires a `const` expression, such as
  annotation arguments or `const val` initializers.
- Java source calls and K1 are not supported.

## Build

```shell
./gradlew build
```

The end-to-end tests compile fixtures separately from their callers, exercising BINARY annotation
retention, compiler-plugin loading, source capture, side-effect removal, structured call-site
construction, Kotlin/JS and Wasm compilation, and relocation-safe Gradle build caching. They also
verify that `evalSourceOf` evaluates its expression exactly once.
