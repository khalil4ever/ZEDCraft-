# ZEDCraft Changelog

## [1.1.0] - 2026-11-08
### Fixed
- **Status Bar & Display Cutout Safe-Area Insets**: Fixed top header rendering underneath the phone's system status bar (time, signal, and battery icons). Added `rememberSafeTopInset()` combining `WindowInsets.statusBars`, `WindowInsets.displayCutout`, and a `28.dp` minimum fallback on `ZedCraftTopHeader` and `OnboardingAndConnectScreen`.
- **Status Bar Background & Icon Contrast**: Configured `SystemBarStyle.dark(#111827)` and `WindowCompat.getInsetsController` in `ZEDCraftTheme` so the status bar matches the dark navy app header with light status bar icons in Dark Studio mode (and dark icons in Light mode).
- **Bottom Navigation Bar Safe Area & Scroll Padding**: Added `rememberSafeBottomInset()` (`WindowInsets.navigationBars` with minimum fallback) to `ZedCraftBottomNavigationBar` and verified bottom scroll padding across all primary tabs (`Home`, `Optimize`, `Research`, `ZD`, `Profile`) and detail screens (`VideoAnalyzerAndAuditScreen`, `ChannelHealthCheckScreen`, `SetupGuideScreen`, `PrivacyAndTermsScreen`, `GitHubUpdaterScreen`, `SavedWorkDetailScreen`).

## [1.0.0] - 2026-11-08
### Added
- Initial release of **ZEDCraft** all-in-one YouTube growth, SEO, analytics, channel health, and ZD AI Studio Android app.
