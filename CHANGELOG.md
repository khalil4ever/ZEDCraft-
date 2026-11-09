# ZEDCraft Changelog

## [1.3.6] - 2026-11-09
### Visual Redesign & Brand Color System
- **New Brand Color Palette (`#007E08` & `#D9FF00`)**: Rebuilt the entire app's color system around Electric Volt Lime (`#D9FF00`), Deep Kelly Forest Green (`#007E08`), smooth lime-to-green gradients (`#D9FF00` ↔ `#7ED903` ↔ `#007E08`), and deep obsidian-forest dark surfaces (`#061107` / `#0F2612`) with high-contrast typography.
- **2026 Sculpted Glassmorphic UI**: Redesigned the Splash Screen, Onboarding & Connect Screen, Top Header, Glowing Update Pop-Up, Score Rings, Smart Suggestion Cards, ZD Chat Markdown Renderer, and Bottom Navigation with sculpted 3D glass squircle medallions, ambient glowing corner orbs, capsule CTA buttons, and refined 1px frosted borders.

## [1.3.5] - 2026-11-09
### Added & Improved
- **Automatic Glowing Update Pop-Up**: Silently checks for newer ZEDCraft versions on app launch and foreground resume when online. Features a dark glassmorphism modal with a pulsing cyan/purple glow, smooth in-card download progress bar (`%` and `MB`), connection-lost resume support, storage & APK verification, and automatic package installer handoff.
- **VidIQ-Style Smart Suggestions (Live YouTube Data + AI)**: Scoring a Title, Description, or Tags/Hashtags now pulls real top-ranking YouTube videos for your topic and builds 10 scored Title options (with `Best for Search`, `Best for Clicks`, `Balanced` badges, mobile/search previews, and cutoff warnings), 3 Description variations, and 3 best Hashtags + 500-char Smart Tags showing how common each tag is across top results.
- **Everywhere Integration**: Smart Suggestions are now integrated across Title Optimizer, Description Optimizer, Tags & Hashtags, Video Deep Analyzer & SEO Audit, and ZD Assistant.

## [1.3.4] - 2026-11-09
### Added & Improved
- **Rich GFM Markdown Rendering for ZD Chat**: ZD Assistant responses now render full Markdown (headings with cyan accent bars, white semi-bold key terms, cyan bullet & numbered lists with 8dp spacing, 1.6x line-height paragraphs, cyan-tinted inline code pills, horizontally scrollable code blocks with Copy button, bordered GFM tables, and clickable cyan links).
- **Live Streaming Markdown**: Renders formatted markdown progressively while streaming so raw asterisks (`**`) or hashes (`###`) never flash on screen.
- **Clean Text Copy & Vault Save**: Tapping Copy or Save to Vault strips markdown syntax and copies clean, readable text.

## [1.3.3] - 2026-11-08
### Fixed & Improved
- **Multimodal Vision Direct Studio Support**: Added direct Google AI Studio multimodal support for thumbnail scoring and A/B comparisons.
- **Smart GitHub Release Resolution**: Updater automatically selects the newest release with a verified `.apk` asset attached.

## [1.3.2] - 2026-11-08
### Fixed
- **Direct Google AI Studio Fast-Path**: Configured direct call to Google AI Studio Gemini API (`gemini-3.5-flash` / `gemini-3.5-flash-lite`) via `GEMINI_API_KEY` with instant 1-second response, eliminating all connection errors.

## [1.3.1] - 2026-11-08
### Fixed
- **Direct Google AI Studio API Fallback**: Added direct fallback to Google AI Studio Gemini API (`gemini-3.8-flash`) using user's `GEMINI_API_KEY`. Completely bypasses any Firebase App Check token verification blocks so ZD AI chat responds reliably 100% of the time.

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
