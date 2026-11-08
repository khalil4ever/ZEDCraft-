# ZEDCraft — Complete Setup & Configuration Guide

**ZEDCraft** is a 100% free, all-in-one YouTube growth, SEO, analytics, and AI strategy application.

---

## 1. Gemini AI Models (Pre-Provisioned via Firebase AI Logic)

ZEDCraft uses **Firebase AI Logic** (`Firebase.ai`) with **Firebase App Check** so your Gemini calls authenticate securely without exposing a client-side Gemini API key in the APK:

- **`ZD_MAIN_MODEL = "gemini-4-argon"`** (with `ThinkingLevel.HIGH`): Powers **ZD** (your AI growth assistant), deep channel strategy, content planning, and video idea generation.
- **`FAST_MODEL = "gemini-3.8-flash"`**: Powers high-speed tools including **Multimodal Vision Thumbnail Scoring**, **A/B Thumbnail Comparison**, **Title Optimizer**, **Description & Chapters Optimizer**, and **Tags & Hashtags Generator**.
- **Automatic Fallback**: Configured in `app/src/main/java/com/example/ai/ZedCraftAiConfig.kt`. If `gemini-4-argon` returns a model-not-found or billing tier error, ZEDCraft automatically falls back to `gemini-3.8-flash` and shows a transparent notice in the UI.

---

## 2. Enabling YouTube Data API v3 & YouTube Analytics API

To pull live public videos, channels, search results, tags, trending videos, and private channel analytics:

1. Open the [Google Cloud Console](https://console.cloud.google.com/) and select your project (`gen-lang-client-0807730600` or your active Google Cloud project).
2. Navigate to **APIs & Services > Library**.
3. Search for and click **Enable** on both:
   - **YouTube Data API v3**
   - **YouTube Analytics API**
4. Navigate to **APIs & Services > Credentials** and click **+ Create Credentials > API key**.
5. *(Recommended)* Restrict the API key to **YouTube Data API v3**.
6. In **Google AI Studio**, open the **Secrets** panel and set:
   - `YOUTUBE_API_KEY` = your YouTube Data API v3 key.

---

## 3. Google Sign-In & YouTube OAuth 2.0 (Read-Only Scopes)

ZEDCraft uses **Google Sign-In** (via Jetpack `CredentialManager`) and **Google Identity Services `AuthorizationClient`** to connect your YouTube channel with **strictly read-only scopes**:

- `https://www.googleapis.com/auth/youtube.readonly` (View your YouTube account, channel metadata, and uploads)
- `https://www.googleapis.com/auth/yt-analytics.readonly` (View your YouTube Analytics reports: views, watch time, average view duration, retention, and traffic sources)

### OAuth Consent Screen Configuration:
1. In the [Google Cloud Console](https://console.cloud.google.com/apis/credentials/consent), open **APIs & Services > OAuth consent screen**.
2. Set App Name to **ZEDCraft** and add your support email.
3. Click **Add or Remove Scopes** and add:
   - `.../auth/youtube.readonly`
   - `.../auth/yt-analytics.readonly`
4. Under **Test users** (while in Testing mode), add the Google account email associated with your YouTube channel.
5. Android SHA-1 and package name (`com.aistudio.zedcraft.ytgrow`) are already registered in `app/google-services.json`.

---

## 4. Quota Conservation & Data Privacy

- **Local Room Caching**: All YouTube Data API v3 and YouTube Analytics API responses are cached locally on-device in SQLite (`ZedCraftLocalDatabase`) to protect your daily 10,000-unit quota (especially 100-unit `search.list` calls, which are cached for 6 hours).
- **Disconnect & Delete Data Anytime**: Open the **Profile** tab at any time to disconnect a channel, clear local API cache, or permanently delete all saved work and chat history.
