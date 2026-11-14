# CWI Core

Native gameplay and integration code for Create Wasteland Industry on Minecraft
1.21.1 / NeoForge. The release artifact is `cwi-core`; the stable mod ID and
registry namespace are `cwi`.

## Building

Use Java 21 and the included Gradle wrapper. Python 3.9 or newer is required for
verification. Platform versions are pinned in `gradle.properties`; local
dependency filenames, SHA-256 hashes and available download URLs are pinned in
`gradle/local-dependencies.json`. Dependency binaries are not included.

```sh
./gradlew restoreLocalDependencies -PdependencySource=/path/to/dependency-jars
./gradlew build -PbuildNumber=20261114.1
```

The dependency source folder supplies artifacts without download URLs. Existing
files with mismatched hashes are preserved and rejected. `build` runs native
GameTests, integration and client/resource contracts, and the distribution audit.
Use `./gradlew compileJava` for an incremental compilation.

Custom texture PNGs and their metadata are supplied externally by the pack's
KubeJS assets. An optional read-only asset check can be enabled with
`-PclientAssets=/path/to/kubejs/assets`. Models, blockstates, translations, audio
and other resource definitions ship in Core. Examples and test recipes are
isolated from the distribution JAR.

Each release JAR records its version, source revision and production-source
SHA-256 in `META-INF/cwi-build.properties`. For builds outside Git, pass
`-PsourceRevision=<commit>` to retain the source identity.

## Third-party notices

Core's own code is All Rights Reserved, as declared in the mod metadata.
Separately attributed material retains its upstream terms. Preserve the notices
in `src/main/resources/META-INF/`, including Bits 'n' Bobs and original TFMG
resource MIT notices, Darker Depths attribution and the upstream audio terms.
