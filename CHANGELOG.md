# ZEDCraft Changelog

## [1.3.0] - 2026-11-08
### Fixed & Improved
- **ZD AI Model Performance**: Switched default chat model to `gemini-3.8-flash` for high-speed, direct responses without requiring a paid Firebase Blaze plan.
- **In-App Auto-Update Notification**: Devices on v1.2.0 or lower will automatically prompt and install this new v1.3.0 update.

## [1.2.0] - 2026-11-08
### Added & Improved
- **ZD AI Chat Resilience & App Check**: Initialized Firebase App Check (`DebugAppCheckProviderFactory` on debug builds with Logcat debug token printing; `PlayIntegrityAppCheckProviderFactory` on release builds), automatic single retry + `gemini-3.8-flash` fallback, and a friendly retry state (`"ZD is having trouble connecting. Please try again."`).
- **Credits Screen**: Replaced the internal API/Setup screen with a clean **Credits** screen honoring creator Timothy Khalil Tembo (Founder of AI Visual CRAFT, based in Zambia).
- **Apply Changes Directly to Live YouTube Videos**: Added `youtube.force-ssl` OAuth support, My Videos selector with Room caching, Before/After preview, safe full-snippet `videos.update` (`title`, `description`, `tags`, `categoryId`, `defaultLanguage`), custom 16:9 thumbnail upload via `thumbnails.set`, local revision backups with **Undo / Restore previous**, and a **View on YouTube** link.
- **In-App GitHub Auto-Updater**: Added silent background release detection for `khalil4ever/ZEDCraft-`, semantic version comparison, in-app APK download with MB/percentage progress bar, and automatic `FileProvider` package installer launch.
- **3D Glass YouTube Play Splash & Loading Screen**: Added dark `#070B14` startup background, 3D glass red play button with sweeping light shine, breathing neon halo, floating sparkles, rainbow circular spinner, and cycling status messages.

## [1.1.0] - 2026-11-08
### Fixed
- **Status Bar & Display Cutout Safe-Area Insets**: Fixed top header rendering underneath the phone's system status bar (time, signal, and battery icons). Added `rememberSafeTopInset()` combining `WindowInsets.statusBars`, `WindowInsets.displayCutout`, and a `28.dp` minimum fallback.
- **Bottom Navigation Bar Safe Area & Scroll Padding**: Added `rememberSafeBottomInset()` (`WindowInsets.navigationBars` with minimum fallback) to `ZedCraftBottomNavigationBar`.

## [1.0.0] - 2026-11-08
### Added
- Initial release of **ZEDCraft** all-in-one YouTube growth, SEO, analytics, channel health, and ZD AI Studio Android app.
