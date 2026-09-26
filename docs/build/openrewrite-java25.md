# OpenRewrite Java 25 migration

Status: optional source-migration tool. It is not part of the complete application gate and does not run during
`clean verify`.

## Preview the recipe

From the repository root on Windows x64:

```powershell
.\tools\java25\run-openrewrite.ps1
```

The runner checksum-provisions the Temurin 25 JDK pinned by `tools/java25/toolchain-lock.json`, uses it to host
Maven, activates the `openrewrite` Maven profile, and runs `rewrite:dryRun`. OpenRewrite reports the generated
patch location without changing tracked source files.

Maven's host JDK matters here. OpenRewrite selects its Java parser from the JVM running Maven, while
`.mvn/toolchains.xml` controls toolchain-aware compiler and test plugins. The runner deliberately sets both
`JAVA_HOME` and `BS2BG_JDK25_HOME` to the verified Temurin 25 installation for the Rewrite process, then restores
the caller's environment.

## Apply or inspect

Apply the configured recipe to the working tree:

```powershell
.\tools\java25\run-openrewrite.ps1 -Goal run
```

List the recipes visible to the plugin:

```powershell
.\tools\java25\run-openrewrite.ps1 -Goal discover
```

The configured recipe is `org.openrewrite.java.migrate.UpgradeToJava25` from
`org.openrewrite.recipe:rewrite-migrate-java`. It is a composite migration: besides setting the Java target, it
can modernize Java APIs and syntax and update Maven plugins or dependencies. This repository already targets
Java 25, so every applied result still needs review against the repository's explicit pins and compatibility
contracts. After `-Goal run`, review `git diff` and run the complete gate:

```powershell
.\tools\java25\verify-java25.ps1
```

## Version and repository policy

`pom.xml` pins `org.openrewrite.maven:rewrite-maven-plugin:6.46.1` and
`org.openrewrite.recipe:rewrite-migrate-java:3.42.1`. These are the latest no-auth releases available from Maven
Central when this setup was added, and both contain the Java 25 migration recipe. They remain opt-in through the
`openrewrite` profile; no Rewrite execution is bound to a Maven lifecycle phase.

Newer OpenRewrite releases are moving to the authenticated Code Genome Project repository. Do not commit a
personal download token or silently add an authenticated repository to the default application build. Updating
these pins should be an explicit review of the recipe changes and distribution terms. See the
[OpenRewrite Java 25 recipe](https://docs.openrewrite.org/recipes/java/migrate/upgradetojava25) and
[Maven plugin reference](https://docs.openrewrite.org/reference/rewrite-maven-plugin).
