# Build notes

## Incremental build, measured 2026-09-11

Worktree `myapp-wt-week1`, branch `feat/week1-app`, Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.10.
Launcher JDK 17; the daemon runs on the JDK 21 toolchain pinned in
`gradle/gradle-daemon-jvm.properties`. Machine: i7-14700K (28 hardware threads), Windows 11,
Git Bash, `nproc` = 28.

Scenario: warm daemon (left over from `:app:testDebugUnitTest`), one-line content change to
`app/src/main/java/com/plainticker/mobile/core/Clock.kt`, then

```bash
time ./gradlew :app:assembleDebug
```

| measurement                         | value                                                      |
|-------------------------------------|------------------------------------------------------------|
| wall clock (`time`)                 | **4.0 s** (Gradle reports `BUILD SUCCESSFUL in 3s`)         |
| tasks                               | 38 actionable: 5 executed, 33 up-to-date                   |
| executed                            | `compileDebugKotlin`, `dexBuilderDebug`, `mergeProjectDexDebug`, `packageDebug`, `createDebugApkListingFileRedirect` |
| configuration cache                 | entry *stored* on this run (first `assembleDebug` with the current build script), so a repeat is a little faster |

For scale, the gate `./gradlew :app:testDebugUnitTest` (12 executed tasks incl. main and test
compilation, 107 tests) took 9 s on the same warm daemon.

So: the edit-build loop is not the problem on this machine. The dangerous phases are clean
builds, `assembleRelease` (R8 full-mode minify + resource shrinking) and anything else that
saturates every core for more than a few seconds.

## CPU mitigation already in `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.workers.max=8
kotlin.daemon.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
```

Why: the CPU throws a fatal machine-check exception under sustained all-core load (see Windows
Event Viewer, WHEA-Logger, before blaming software). `org.gradle.workers.max=8` caps Gradle's
task fan-out well below the 28 threads it would otherwise use; the build cache and
configuration cache keep the incremental window at a few seconds, which is what the table
above shows. The block is marked in `gradle.properties` for deletion once the CPU is
replaced.

## Fallback if a freeze recurs

1. `org.gradle.workers.max=8` -> `4` in `gradle.properties`. Expect a clean build to roughly
   double; the incremental loop barely changes (it is 5 tasks).
2. Keep Turbo Boost off in BIOS. Do not switch it back on to win the time back.
3. Next lever, untested: `kotlin.daemon.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4`,
   because `workers.max` does not bound the Kotlin daemon's internal thread pool.
4. `assembleRelease` never runs locally. Releases come from `.github/workflows/release.yml`.
5. One Gradle invocation at a time; never two worktrees building at once.

## CI

`.github/workflows/ci.yml` installs Temurin 21 so the pinned daemon toolchain is present
without a foojay download, then runs `./gradlew :app:testDebugUnitTest --no-daemon`. Compile
target stays Java 17 (`compileOptions`). The `redaction-guard` job runs
`scripts/redaction-guard.sh` on the tree and on the full history.
