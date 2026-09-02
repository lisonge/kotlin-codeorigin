# Changelog

## 0.2.0

- Allow `declarationSourceOf<Type>()` and `declarationSourceOf(::reference)` to capture declarations
  from another source file when its source is available in the current compilation.
- Document the incremental-compilation limitations of cross-file declaration source capture.
- Read Maven Central publishing and signing configuration from release environment variables.

## 0.1.0

- Initial CodeOrigin public API and module layout.
- Add `nameOf(value)`, `nameOf<Type>()`, `sourceOf(expression)`, and `evalSourceOf(expression)`
  compiler intrinsics.
- Add same-file `declarationSourceOf<Type>()` and `declarationSourceOf(::reference)` intrinsics.
- Redesign kotlin-loc call-site injection as `@CallSite` with String, Int, and `SourceLocation`
  targets.
- Add Kotlin Multiplatform API artifacts, Gradle plugin integration, and JVM end-to-end tests.
- Preserve leading KDoc, multiline modifiers, and use-site annotations in declaration source.
- Inherit `@CallSite` metadata through override hierarchies and diagnose conflicting formats.
- Reject evaluated property receivers in `nameOf` and parse commented type arguments safely.
- Make compiler options and captured paths relocation-safe for Gradle build-cache reuse.
- Add published-plugin functional tests for JVM, Kotlin/JS, Wasm, and relocated projects.
