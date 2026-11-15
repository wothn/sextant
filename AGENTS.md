# Repository Guidelines

## Project Boundaries

Sextant is a native Android personal finance app using Kotlin and Jetpack Compose. The app module is `:android:app`; source is under `android/app/src/main/java/com/sextant/app/`.

- `data/` owns local persistence, schema and seed data, ledger calculations, and repositories/stores. Persistence is Room: entities live in `SextantEntities.kt`, queries in the `*Dao.kt` files, and schema changes go through `SextantDatabase` (bump the version, add a `Migration`, commit the regenerated JSON under `android/app/schemas/`).
- Room rejects main-thread queries, so repository and store methods are `suspend`. UI call sites run them from `LaunchedEffect` or a `rememberCoroutineScope()`; do not re-enable `allowMainThreadQueries()` in production code.
- `automation/` owns notification parsing, capture coordination, workers, and the listener service.
- `ui/` owns Compose screens and components. UI calls repositories rather than issuing SQL; money and date formatting belongs in `ui/util/DisplayFormatters.kt`.
- `SextantApplication.kt` wires dependencies; `MainActivity.kt` and `ui/SextantApp.kt` host the screen flow.


## Validation

Use the checked-in Gradle wrapper (`.\gradlew.bat` on Windows, `./gradlew` on Unix).

- During development, run the narrowest relevant checks. Before handing off code changes, run `.\gradlew.bat :android:app:testDebugUnitTest`.
- Also run `:android:app:assembleDebug` for UI, resources, manifest, or build/dependency changes. The combined command is `.\gradlew.bat :android:app:testDebugUnitTest :android:app:assembleDebug`; regular CI runs only the unit tests.
- Add `--rerun` after the test task when real execution is needed despite `UP-TO-DATE`. Documentation-only changes do not require Android tests.
- Report actual validation results and any unverified scope.

## Testing

Local tests live under `android/app/src/test/java/com/sextant/app/`. Follow existing `*Test.kt` examples, especially `NotificationParsersTest` and `SextantRepositoryTest`, rather than adding new scaffolding.

- Use JUnit 4 for pure logic; use Robolectric when Android APIs are required, and the declared `work-testing` dependency for WorkManager cases.
- Database tests use `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [35])`, and `deleteDatabase("sextant.db")` in both `@Before` and `@After`. Also call `SextantDatabase.closeForTests()` in both hooks: an open connection keeps the deleted file alive, and the next test would otherwise read and write a database that no longer matches what is on disk.
- Repository and store calls are suspend. Wrap a test body in `runBlocking` (or join an existing block) instead of reintroducing synchronous database access.
- Preserve the Robolectric `--add-opens` JVM arguments in `android/app/build.gradle.kts`.
- Date and month-boundary tests pin the timezone and restore it in `@After`.
- Assert observable behavior, especially invalid input, transactional rollback, idempotency, and stale-timestamp conflicts. Assert exact user-visible text when that wording is part of the expected behavior.
- Schema changes extend `SextantDatabaseTest` for creation, indexes, seed data, and the migration path from the previous version; keep `BackupCodecTest` aligned with `SextantDatabase.DATABASE_VERSION`.
- Test a migration against the schema that actually exists on disk, not the one the source intends. Copy the old `CREATE TABLE` / `CREATE INDEX` statements from `sqlite_master` (`SELECT sql FROM sqlite_master WHERE sql IS NOT NULL`), including inline `UNIQUE`, `DESC` ordering, and partial-index `WHERE` clauses — Room's validator compares all of those, so a hand-written fixture that quietly "corrects" them passes while real devices crash on upgrade. Verify risky upgrades on a device or emulator with a real pre-upgrade database.
- Add device tests under `android/app/src/androidTest/` only for flows that need a device.

## Change Discipline

- Keep changes scoped and preserve existing user edits. Do not edit generated output in `android/app/build/`, root `build/`, `.gradle/`, or `.kotlin/`.
- PRs describe the resulting behavior, relevant test/build results, linked issues when applicable, and screenshots or recordings for visible UI changes.

## Commit Messages

- 遵循 Conventional Commits：`type(scope): 中文摘要`，`scope` 可省略；类型和作用域保留英文，如 `fix(update): 修复更新说明显示`。
- 标题简洁说明变更；正文与标题空一行，按需用中文补充原因和关键改动，避免只有标题无法说明清楚的提交。
