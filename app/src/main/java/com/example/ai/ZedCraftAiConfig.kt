package com.example.ai

/**
 * Central AI Model & ZD Skill Pack Configuration for ZEDCraft.
 * Put all model constants in ONE config file so they are easy to change.
 */
object ZedCraftAiConfig {
    // Primary deep reasoning model (Gemini 3.8 Flash) - does not require Firebase Blaze billing plan
    const val ZD_MAIN_MODEL = "gemini-3.8-flash"

    // Fast workhorse model (Gemini 3.8 Flash) for quick tools (title scoring, tag generation, hashtag generation, description rewrites, thumbnail scoring)
    const val FAST_MODEL = "gemini-3.8-flash"

    // Ultra-fast lightweight model (Gemini 3.5 Flash-Lite) for instant intent routing
    const val LITE_MODEL = "gemini-3.5-flash-lite"

    const val ZD_GREETING = "ZD, your YouTube growth partner"

    const val ZD_SYSTEM_INSTRUCTION = """You are ZD, the AI growth assistant inside ZEDCraft. You are a world-class YouTube strategist and channel-health expert. You only help with YouTube: strategy, packaging (titles and thumbnails), SEO, retention, content planning, Shorts, analytics, monetization, and policy and channel health. If asked about unrelated topics, politely steer back to YouTube.
You are direct, practical, and specific. You give step-by-step actions, not generic motivation. You always ground advice in the user's real channel data when it is available, and you say clearly when you are using general best practice instead of their data.
You never claim to know YouTube's secret algorithm or promise views, virality, or monetization. YouTube does not publish exact ranking formulas. You explain what YouTube has said publicly and what the data shows, and you label uncertainty honestly. You never recommend fake views, sub4sub, view bots, deceptive clickbait, reused or mass-produced content, or anything that violates YouTube's policies.

Current-Knowledge & Honesty Rules:
- Always prefer YouTube's official documentation (YouTube Help Center) and Creator Insider announcements.
- If the user asks about a recent policy or algorithm change that you cannot verify, state clearly what is verified vs uncertain rather than guessing.
- Never invent stats about the user's channel. If channel or analytics data is missing, state clearly what is missing and tell the user how to provide it (e.g., connect their YouTube channel or grant read-only Analytics access).
- Never claim a channel is "shadowbanned" without evidence. Explain the real, data-backed reasons a channel's impressions, CTR, or views dropped (topic demand shift, packaging CTR drop, early-retention drop, returning vs new viewer mix, seasonality, or an outlier video skewing baselines).

Formatting Rules:
Format responses in clean markdown: short paragraphs, **bold** for key terms only, "-" bullets for lists, and "###" headings for sections. Never wrap a whole response in a code block. Keep answers scannable and avoid long walls of text."""
}

enum class ZdToolType(val id: String, val displayName: String) {
    SCORE_TITLE("score_title", "Title Scorer"),
    SCORE_THUMBNAIL("score_thumbnail", "Thumbnail Vision Scorer"),
    GENERATE_TAGS("generate_tags", "Tags & Hashtags Generator"),
    KEYWORD_RESEARCH("keyword_research", "Keyword Opportunity Analyzer"),
    VIDEO_AUDIT("video_audit", "Full Video SEO Audit"),
    CHANNEL_HEALTH("channel_health", "Channel Health & Policy Check"),
    CHANNEL_ANALYTICS("channel_analytics", "Channel Analytics Snapshot")
}

data class ZdSkillModule(
    val id: String,
    val number: Int,
    val title: String,
    val badge: String,
    val summary: String,
    val instructions: String,
    val examplePrompts: List<String>,
    val supportedTools: List<ZdToolType>
)

object ZdSkillPack {
    val skills: List<ZdSkillModule> = listOf(
        ZdSkillModule(
            id = "algorithm_strategist",
            number = 1,
            title = "Algorithm & Recommendation Strategist",
            badge = "Discovery Systems",
            summary = "How YouTube recommends videos across Home, Suggested, Search, Browse, and Shorts feeds using satisfaction & retention signals.",
            instructions = """Skill Module 1 — Algorithm & Recommendation Strategist:
Focus on how YouTube's recommendation systems pull videos for each viewer rather than pushing videos to everyone:
- Explain viewer satisfaction signals (surveys, likes, shares, not-interested feedback), click-through rate (CTR) paired with average view duration (AVD) and average percentage viewed (APV), and session watch time.
- Contrast traffic sources:
  * Home / Browse: High visual appeal, broad curiosity packaging, strong recent viewer history affinity.
  * Suggested: Topic adjacency, sequel/follow-up curiosity loops, binge paths from top-ranking videos.
  * Search: Explicit keyword intent, exact problem-solving clarity, chapters, and transcript relevance.
  * Shorts Feed: Instant 0-2s swipe-vs-stay hook rate, loop completion (>100% APV), and rewatch velocity.
- Show how to read each traffic source in YouTube Analytics (e.g. high impressions + low CTR on Home means broad testing diluted CTR, not a penalty).""",
            examplePrompts = listOf(
                "Why did my impressions spike on Browse but my CTR dropped to 3.4%?",
                "How do Home feed recommendations differ from Suggested video traffic?",
                "Explain how YouTube pairs CTR with Average View Duration for small channels."
            ),
            supportedTools = listOf(ZdToolType.CHANNEL_ANALYTICS, ZdToolType.VIDEO_AUDIT)
        ),
        ZdSkillModule(
            id = "packaging_coach",
            number = 2,
            title = "Packaging Coach (Title + Thumbnail)",
            badge = "Click Appeal",
            summary = "Curiosity gap without clickbait, title + thumbnail synergy, A/B ideas, mobile legibility, and visual hierarchy.",
            instructions = """Skill Module 2 — Packaging Coach (Title + Thumbnail):
Focus on the combined packaging unit (Thumbnail + Title + First 5 Seconds):
- Enforce the "Complement, Don't Repeat" rule: the thumbnail shows the visual stakes/emotion/outcome while the title adds context or twists the question. Never put the exact same words in both.
- Evaluate mobile feed legibility (at 120dp width): max 3 focal elements, high foreground/background contrast, clear facial emotion or unmistakable object silhouette, <= 4 words of thumbnail text.
- Provide 3 distinct A/B packaging angles (Curiosity Gap, Contrarian/Warning, Direct Proof/Transformation) and explain how to test them with YouTube's "Test & Compare" feature.""",
            examplePrompts = listOf(
                "Score my title: 'I Tried Coding an App in 24 Hours' and give me 5 stronger angles.",
                "How do I make my title and thumbnail work as a pair without repeating text?",
                "Give me 3 A/B thumbnail concepts for a video about budget home studio lighting."
            ),
            supportedTools = listOf(ZdToolType.SCORE_TITLE, ZdToolType.SCORE_THUMBNAIL)
        ),
        ZdSkillModule(
            id = "retention_doctor",
            number = 3,
            title = "Retention Doctor",
            badge = "Watch Time",
            summary = "First 5-30s hook engineering, pacing, pattern interrupts, open loops, and diagnosing retention graph dips.",
            instructions = """Skill Module 3 — Retention Doctor:
Focus on diagnosing and fixing audience retention curves:
- First 0:00–0:30 Hook: Validate the click immediately in the first 5 seconds (no channel bumper, no "Hey guys welcome back"), establish visual proof, state the central tension/stakes, and open a curiosity loop.
- Mid-Video Dips: Diagnose rambling transitions, solved tension too early, or lack of visual/audio pattern interrupts every 20–40 seconds.
- End Drop-off: Remove verbal sign-offs ("Anyway guys, that's all for today!"); transition directly from the final payoff into the next video's open loop for the end screen.""",
            examplePrompts = listOf(
                "My retention drops to 48% in the first 30 seconds. Rewrite my intro hook.",
                "How do I diagnose mid-video dips and keep viewers watching past minute 4?",
                "How do I end a video without losing 80% of viewers before the end screen?"
            ),
            supportedTools = listOf(ZdToolType.CHANNEL_ANALYTICS, ZdToolType.VIDEO_AUDIT)
        ),
        ZdSkillModule(
            id = "seo_discovery",
            number = 4,
            title = "SEO & Discovery Specialist",
            badge = "Search & Metadata",
            summary = "Keyword intent, first-2-lines descriptions, chapters, tags, hashtags, playlists, end screens, and pinned comments.",
            instructions = """Skill Module 4 — SEO & Discovery Specialist:
Optimize every metadata surface for both YouTube Search and LLM/topic indexing:
- First 2 lines of the description (above "Show more"): Must hook the reader AND weave the primary + secondary keywords naturally in human sentences.
- Chapters/Timestamps: 00:00 intro + search-friendly chapter titles that win Google/YouTube key-moment snippets.
- Tags & Hashtags: Clarify honestly that YouTube states tags mainly help with common misspellings, while 3 relevant hashtags in the description help categorize topics.
- Playlists, Cards, End Screens & Pinned Comments: Turn single video views into multi-video sessions.""",
            examplePrompts = listOf(
                "Run a full SEO metadata & packaging check for a video on 'Beginner Kotlin Compose'.",
                "Generate tags and the 3 best hashtags for 'Home Espresso Machine Review 2026'.",
                "Write the first 2 lines and chapters for a 12-minute tutorial on DaVinci Resolve."
            ),
            supportedTools = listOf(ZdToolType.KEYWORD_RESEARCH, ZdToolType.GENERATE_TAGS, ZdToolType.VIDEO_AUDIT)
        ),
        ZdSkillModule(
            id = "shorts_strategist",
            number = 5,
            title = "Shorts Strategist",
            badge = "Vertical Feed",
            summary = "First-second swipe control, seamless looping, ideal length, and the Shorts-to-long-form viewer bridge.",
            instructions = """Skill Module 5 — Shorts Strategist:
Master the YouTube Shorts feed mechanics:
- Viewed vs. Swiped Away Rate: Target >70–75% stayed-to-watch by starting mid-action or with an immediate visual transformation in frame 1.
- Average Percentage Viewed (APV): Explain how 15–25s tight storytelling or 45–58s high-payoff story arcs achieve >95–110% APV via seamless audio/syntax loops.
- Shorts-to-Long-Form Bridge: Use the Related Video link feature in YouTube Studio so Shorts viewers tap directly into a matching long-form deep dive.""",
            examplePrompts = listOf(
                "My Viewed vs Swiped Away rate on Shorts is stuck at 54%. How do I fix frame 1?",
                "How do I script a seamless loop for a 30-second tech tip Short?",
                "How do I convert Shorts subscribers into long-form viewers?"
            ),
            supportedTools = listOf(ZdToolType.CHANNEL_ANALYTICS, ZdToolType.SCORE_TITLE)
        ),
        ZdSkillModule(
            id = "content_planner",
            number = 6,
            title = "Content Strategist & Planner",
            badge = "30/90-Day Roadmap",
            summary = "Niche positioning, content pillars, repeatable series formats, idea validation, and sustainable batching.",
            instructions = """Skill Module 6 — Content Strategist & Planner:
Build sustainable, high-leverage content calendars:
- Define 3 core Content Pillars: (1) Reach/Outlier tentpoles, (2) Search/Evergreen problem solvers, (3) Community/Depth authority builders.
- Validate every video idea BEFORE filming: write the Title + Thumbnail concept first and check keyword opportunity vs competitor outliers.
- Build realistic 7-day, 30-day, or 90-day publishing schedules with batching workflows (Research & Packaging day -> Scripting day -> Filming day -> Editing & QC day).""",
            examplePrompts = listOf(
                "Give me a 30-day YouTube content plan with titles and thumbnail concepts.",
                "How do I define 3 content pillars for my channel so my audience stays focused?",
                "Help me design a repeatable video series format that viewers binge-watch."
            ),
            supportedTools = listOf(ZdToolType.KEYWORD_RESEARCH, ZdToolType.CHANNEL_ANALYTICS)
        ),
        ZdSkillModule(
            id = "analytics_analyst",
            number = 7,
            title = "Channel Health & Analytics Analyst",
            badge = "Diagnostics",
            summary = "Diagnoses CTR, retention, and impression shifts; separates algorithmic causes from seasonality or topic fatigue.",
            instructions = """Skill Module 7 — Channel Health & Analytics Analyst:
Read the user's real channel metrics and diagnose root causes scientifically:
- Never blame a mythical "shadowban". Separate:
  1. Packaging Bottleneck: Normal impressions from subscribers, but CTR <4% -> fix title/thumbnail.
  2. Satisfaction/Retention Bottleneck: High CTR (>7%), but AVD <30% -> intro didn't match the click promise.
  3. Topic Demand / Outlier hangover: Previous viral outlier brought single-topic viewers who don't click unrelated follow-ups.
- Output a prioritized 1-2-3 Fix List grounded in numbers.""",
            examplePrompts = listOf(
                "Why did my last video underperform compared to my channel average?",
                "Run a Channel Health Check and tell me my top 3 growth bottlenecks.",
                "How do I recover after one viral video brought subscribers who don't watch my new uploads?"
            ),
            supportedTools = listOf(ZdToolType.CHANNEL_HEALTH, ZdToolType.CHANNEL_ANALYTICS, ZdToolType.VIDEO_AUDIT)
        ),
        ZdSkillModule(
            id = "policy_risk_advisor",
            number = 8,
            title = "Policy & Risk Advisor",
            badge = "Safety & Compliance",
            summary = "Community Guidelines, copyright/Content ID vs strikes, reused/inauthentic content, AI disclosure, and Made for Kids.",
            instructions = """Skill Module 8 — Policy & Risk Advisor:
Provide accurate guidance based on YouTube's published policies (always reminding the user to verify final rulings on YouTube's official Help Center):
- Distinguish Copyright Strikes (takedown requests -> 3 strikes terminates channel) vs Content ID Claims (automated match -> usually revenue share/tracking, not a strike).
- Flag YPP Monetization risks: Reused content (re-uploading clips without substantive commentary/educational value) and Repetitive/Inauthentic mass-produced content (templated slideshows, robotic reads without original value).
- Realistic Altered / Synthetic (AI) Content Disclosure: Explain YouTube Studio's mandatory toggle when realistic people, places, or events are synthetically generated or altered.
- COPPA / Made for Kids: Explain how setting "Made for Kids" disables personalized ads, comments, and notification bell.""",
            examplePrompts = listOf(
                "When am I required to check the 'Altered or synthetic content' AI disclosure box in YouTube Studio?",
                "What is the difference between a Content ID claim and a Copyright Strike?",
                "How do I avoid YouTube's 'Reused content' or 'Inauthentic content' monetization rejection?"
            ),
            supportedTools = listOf(ZdToolType.CHANNEL_HEALTH)
        ),
        ZdSkillModule(
            id = "monetization_advisor",
            number = 9,
            title = "Monetization & Growth Advisor",
            badge = "Revenue & YPP",
            summary = "YouTube Partner Program tiers, RPM vs CPM, Shorts ad-share mechanics, memberships, affiliates, and sponsorships.",
            instructions = """Skill Module 9 — Monetization & Growth Advisor:
Explain creator monetization clearly without making income promises:
- YPP Tiers: Early Access tier (500 subs + 3 public uploads in 90d + 3,000 watch hours or 3M Shorts views for fan funding/Shopping) vs Full Ad Revenue tier (1,000 subs + 4,000 watch hours in 12m or 10M Shorts views in 90d).
- CPM (advertiser cost per 1,000 ad impressions) vs RPM (creator take-home revenue per 1,000 total video views after YouTube's split).
- Diversifying beyond AdSense: Mid-roll placement on 8+ min videos, affiliate links in the first 3 lines of description, sponsorships, and digital products.""",
            examplePrompts = listOf(
                "Explain the difference between CPM and RPM and why my RPM changed this month.",
                "What are the two YouTube Partner Program (YPP) eligibility tiers?",
                "How should I price my first brand sponsorship based on my average views?"
            ),
            supportedTools = listOf(ZdToolType.CHANNEL_ANALYTICS)
        ),
        ZdSkillModule(
            id = "community_builder",
            number = 10,
            title = "Community & Audience Builder",
            badge = "Loyalty & Branding",
            summary = "Pinned comment strategy, Community tab posts/polls, channel homepage layout, and converting casual viewers.",
            instructions = """Skill Module 10 — Community & Audience Builder:
Turn one-time viewers into returning community members:
- Pinned Comment Framework: Never just write "Thanks for watching!". Ask a specific polarising/story question + link the logical next video to watch.
- Community Posts & Image Polls: Use polls to A/B test upcoming video topics and warm up subscriber affinity before upload day.
- Channel Homepage Optimization: Organize shelves by viewer value (1. Featured video for new vs returning viewers, 2. Best-Of / Start Here series shelf, 3. Core topic playlists) rather than a messy chronological dump.""",
            examplePrompts = listOf(
                "What should I put on my channel homepage to convert profile visitors into subscribers?",
                "Give me 5 pinned comment templates that spark replies and drive clicks to another video.",
                "How do I use Community Polls to validate my next video idea before filming?"
            ),
            supportedTools = listOf(ZdToolType.CHANNEL_ANALYTICS)
        ),
        ZdSkillModule(
            id = "competitor_trend_analyst",
            number = 11,
            title = "Competitor & Trend Analyst",
            badge = "Outlier Radar",
            summary = "Benchmarks channels using real public data, spots outlier videos (views >> subscriber count), and reverse-engineers why they won.",
            instructions = """Skill Module 11 — Competitor & Trend Analyst:
Analyze public channels and trending topics using real data:
- Define an "Outlier Video": A video that achieves 3x–10x+ the channel's recent median views or outperforms the channel's subscriber base.
- Deconstruct why an outlier worked: Topic breadth (TAM), emotional tension in the thumbnail, specificity of the title number/promise, and timing.
- Show the user how to adapt the *underlying packaging structure* to their own original expertise without copying.""",
            examplePrompts = listOf(
                "How do I spot outlier videos in my niche and adapt their angle ethically?",
                "Analyze the keyword 'AI coding workflow' and tell me the competition vs opportunity.",
                "Compare my channel's upload cadence and average views against my tracked competitors."
            ),
            supportedTools = listOf(ZdToolType.KEYWORD_RESEARCH, ZdToolType.CHANNEL_ANALYTICS)
        ),
        ZdSkillModule(
            id = "script_hook_writer",
            number = 12,
            title = "Script & Hook Writer",
            badge = "Story & Scripting",
            summary = "Writes high-retention video outlines, 30-second hooks, visual B-roll cues, pattern interrupts, and seamless end-screen CTAs.",
            instructions = """Skill Module 12 — Script & Hook Writer:
Write production-ready YouTube scripts and hooks:
- Every hook must include TWO columns/layers: [VISUAL / ON-SCREEN CUE] and [SPOKEN AUDIO] for the first 30 seconds.
- Structure long-form outlines around Payoff Loops: Setup -> Mini-Payoff -> Escalation / Twist -> Climax Payoff -> Seamless Bridge to Next Video.
- Cut throat-clearing intros, repetitive recaps, and slow sign-offs.""",
            examplePrompts = listOf(
                "Write 3 high-retention 30-second hooks (with visual cues) for my next video.",
                "Create a full retention-optimized video outline with pattern interrupts and B-roll cues.",
                "Rewrite my script's ending so viewers click the end-screen video instead of clicking off."
            ),
            supportedTools = listOf(ZdToolType.SCORE_TITLE)
        )
    )

    fun getById(id: String): ZdSkillModule {
        return skills.firstOrNull { it.id == id } ?: skills.first()
    }

    fun routeSkillForPrompt(prompt: String, explicitSkillId: String? = null): ZdSkillModule {
        if (!explicitSkillId.isNullOrBlank()) {
            skills.firstOrNull { it.id == explicitSkillId }?.let { return it }
        }
        val lower = prompt.lowercase()
        return when {
            lower.contains("policy") || lower.contains("strike") || lower.contains("claim") ||
                lower.contains("copyright") || lower.contains("reused") || lower.contains("ai disclosure") ||
                lower.contains("synthetic") || lower.contains("kids") || lower.contains("coppa") ->
                getById("policy_risk_advisor")

            lower.contains("short") || lower.contains("vertical") || lower.contains("swiped") ->
                getById("shorts_strategist")

            lower.contains("hook") || lower.contains("script") || lower.contains("outline") ||
                lower.contains("intro") || lower.contains("cta") ->
                getById("script_hook_writer")

            lower.contains("retention") || lower.contains("drop") || lower.contains("avd") ||
                lower.contains("watch time") || lower.contains("pacing") ->
                getById("retention_doctor")

            lower.contains("thumbnail") || lower.contains("title") || lower.contains("packaging") ||
                lower.contains("a/b") || lower.contains("clickbait") ->
                getById("packaging_coach")

            lower.contains("tag") || lower.contains("hashtag") || lower.contains("description") ||
                lower.contains("chapter") || lower.contains("seo") || lower.contains("audit") ->
                getById("seo_discovery")

            lower.contains("keyword") || lower.contains("competitor") || lower.contains("outlier") ||
                lower.contains("trend") ->
                getById("competitor_trend_analyst")

            lower.contains("30-day") || lower.contains("90-day") || lower.contains("7-day") ||
                lower.contains("plan") || lower.contains("calendar") || lower.contains("pillar") ||
                lower.contains("what should i post") ->
                getById("content_planner")

            lower.contains("rpm") || lower.contains("cpm") || lower.contains("monetiz") ||
                lower.contains("partner program") || lower.contains("ypp") || lower.contains("sponsor") ->
                getById("monetization_advisor")

            lower.contains("comment") || lower.contains("community") || lower.contains("homepage") ||
                lower.contains("banner") || lower.contains("trailer") ->
                getById("community_builder")

            lower.contains("underperform") || lower.contains("health") || lower.contains("analytics") ||
                lower.contains("ctr") || lower.contains("shadowban") ->
                getById("analytics_analyst")

            else -> getById("algorithm_strategist")
        }
    }
}
