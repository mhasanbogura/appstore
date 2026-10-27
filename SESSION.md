# App Store — Session Log

## App Identity
- Package: `com.mahmuduls.appstore`
- Current Version: `1.5.36` (versionCode=36)
- Increment: +1 per build (changed from +2)

## Drive Config
- Primary folder ID: `1PBrhSIvDk0QrgNS6XeTeA1RDLFPeTqKV`
- API key: configurable in Settings (default: `AIzaSyA4ymjFIbuGVhFsKjxVV46RT-qWqNHNiY4`)
- APK naming: `AppName_packageName_vX.Y.Z.apk`
- Icon naming: `AppName_packageName.png` (same folder)
- Shared folders: added in Settings, each can have `update/` subfolder

## All Changes Made

### Update Config — root-only, Android-only (v1.10.28)
- `Update.md` now lives ONLY in the root Drive folder (deleted the `Android\Update.md`
  copy). Android subfolder syncs find it via the parent-folder lookup
- `syncDriveFolder` skips Update.md entirely when `platform != "Android"` —
  Windows/Web syncs ignore the config (verified: Windows folder logs `configFile=NONE`)
- `fetchMdContent` retries up to 3× with 700ms backoff — a single transient Drive
  failure no longer silently disables the whole config (was: any failure → empty
  config → all updates stayed ON)
- Added diagnostic logging under tag `UpdateConfig`: folder ID, platform, whether
  Update.md was found (direct vs parent), content length, parsed disabled packages,
  and the final `updateCheck=false` apps
- Verified on emulator (BlueStacks): Manage Apps shows "0 updates available — All
  applications are up to date" after Nova Launcher + Poweramp are listed in Update.md

### Login + Update Config (v1.10.27)
- Google login now shows Google's REAL consent screen: added
  `requestServerAuthCode(default_web_client_id, true)` (forceConsent) — same setup
  as Meter Manager. Removed the fake `GoogleConsentDialog` from all auth screens
- Update.md fix: sync only looked for Update.md inside the synced folder, but the
  Android/Windows shared folders are SUBFOLDERS of the main Drive folder where
  Update.md lives → config was never applied. Sync now falls back to the parent
  folder's Update.md
- Update.md data: uncommented entries, fixed Poweramp package
  (app.maxmpz.audioplayer → com.maxmpz.audioplayer), copied Update.md into
  Android\ so already-released apps apply it on next sync

### Build System (Windows machine setup)
- Installed JDK 21 (Temurin) via winget — no JDK existed on the Windows machine
- `settings.gradle.kts`: auto-detects SDK location per OS and fixes `local.properties`
  `sdk.dir` (Linux box uses `~/android-sdk`, Windows uses `%LOCALAPPDATA%\Android\Sdk`)
  — the folder is synced via Google Drive so the old hardcoded Linux path broke Windows builds
- `fixKeystore` task in `app/build.gradle.kts`: validates `debug.keystore` (alias
  `androiddebugkey`, password `android`) and regenerates it with the JDK's keytool if
  missing/corrupt. Runs automatically via `preBuild` — builds self-heal
  Warning: regeneration changes the signing key; old installs need a reinstall
- Removed duplicate launcher icons: default template `ic_launcher*.webp` (tracked, May)
  conflicted with the custom blue PNG icons (Sep 21) and failed `packageDebugResources`
- Debug builds excluded publish tasks on Windows:
  `gradlew :app:assembleDebug -x :app:publishToDrive -x :app:incrementVersion -x :app:releaseToGitHub`
  (publishToDrive/releaseToGitHub target the Linux machine's paths and gh CLI)

### Admin Removed
- Removed admin login/logout, admin edit/delete dialogs, AdminTab
- Removed credentials sync, admin accounts, all admin-related code
- Settings exposes Drive API key, folder IDs, shared folders to all users

### Version System
- `version.properties`: `versionCode=N`, `versionName=1.5.N`
- `incrementVersion` task: adds +1 to versionCode, sets versionName last segment
- `renameApkForUpload`: copies APK as `App Store_com.mahmuduls.appstore_vX.Y.Z.apk`
- `generateManifestJson`: creates `store_manifest.json`
- Build chain: `assembleDebug` → `renameApkForUpload` → `generateManifestJson` → `incrementVersion`

### Icon System
- Drive icon lookup: queries PNG/JPG in same Drive folder, matches by filename key (`AppName_packageName`)
- `AppStoreApplication` class with Coil disk cache (2% storage in `cacheDir/image_cache`)
- Downloads tab shows `AsyncImage` with iconUrl from store app (falls back to download icon)

### Download System
- Downloads write to `{package}_{versionCode}.tmp`, rename to `.apk` on completion
- Failed/cancelled `.tmp` files deleted on cancellation
- Downloads tab scans both `.apk` and `.tmp` files, plus synthetic entries from `downloadStates`
- `downloadedFiles` recomputes on `downloadStates` changes (dynamic updates)
- `yield()` before each `read()` loop for responsive cancellation
- `activeCalls` (ConcurrentHashMap) stores OkHttp `Call` for instant network abort on cancel
- Cancel resets state to `Idle`, deletes `.tmp` file, removes from active jobs/calls
- Auto-install on download complete (no separate Install button needed)
- Existing cached `.apk` detected and installed directly (no re-download)
- Auto-retry failed downloads after 10 minutes

### Notification System
- Init: creates two channels (`app_store_updates`, `app_store_downloads`)
- Update notification: tap opens Manage Apps tab (tab 1)
- Download complete notification: tap opens Downloaded tab (tab 2)
- Download failed notification: tap opens Store tab (tab 0)
- `REQUEST_DELETE_PACKAGES` permission added for Android 14+
- Auto-update notifications default: `true`

### UI Features
- Store tab: long-press installed → uninstall dialog, not-installed → install dialog
- Full-width `Button` in dialog (same style as store install button), no Cancel button
- App icon in dialog title and text
- Manage Apps tab: updates + installed apps with cancel buttons on in-progress downloads
- Downloads tab: progress spinner + cancel for in-progress, install/delete for completed
- Delete All skips apps in `Progress` or `Completed` state
- Dark mode default: `true` (OLED black `#000000`)
- Pull-to-refresh on Store, Manage Apps, Downloads tabs

### Self-Update Detection
- Store tab `hasUpdate`: checks `versionCode > installedVersion && versionName != installedVerName`
- Version display: `v1.5.22 → v1.5.24` (no versionCode suffix)

## Key Files
- `app/build.gradle.kts` — build config, tasks
- `version.properties` — version state
- `app/src/main/AndroidManifest.xml` — permissions, app class
- `app/src/main/java/com/example/MainActivity.kt` — all composables
- `app/src/main/java/com/example/ui/viewmodel/StoreViewModel.kt` — business logic
- `app/src/main/java/com/example/data/repository/AppRepository.kt` — Drive sync
- `app/src/main/java/com/example/util/NotificationHelper.kt` — notifications
- `app/src/main/java/com/example/AppStoreApplication.kt` — Coil disk cache init
- `app/src/main/java/com/example/ui/theme/Color.kt` — OLED palette
- `app/src/main/java/com/example/data/remote/DriveApiService.kt` — Drive API
