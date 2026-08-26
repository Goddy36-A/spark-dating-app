-- =============================================================================
-- Spark Dating App — Initial Schema
-- Run via: supabase db push  (or paste into Supabase SQL editor)
-- =============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";        -- for proximity queries
CREATE EXTENSION IF NOT EXISTS "pg_trgm";        -- for fuzzy text search

-- =============================================================================
-- ENUMS
-- =============================================================================

CREATE TYPE gender AS ENUM ('man', 'woman', 'non_binary', 'other', 'unspecified');
CREATE TYPE relationship_intent AS ENUM ('long_term', 'casual', 'friendship', 'unsure', 'unspecified');
CREATE TYPE message_type AS ENUM ('text', 'image', 'gif');
CREATE TYPE report_category AS ENUM (
    'harassment', 'spam', 'fake_profile', 'inappropriate_content',
    'scam_fraud', 'impersonation', 'underage_concern', 'other'
);
CREATE TYPE report_status AS ENUM ('pending', 'under_review', 'resolved', 'dismissed');
CREATE TYPE subscription_tier AS ENUM ('free', 'plus', 'gold', 'platinum');
CREATE TYPE subscription_status AS ENUM ('active', 'cancelled', 'expired', 'grace_period', 'on_hold');
CREATE TYPE notification_type AS ENUM (
    'new_match', 'new_message', 'new_like', 'new_super_like',
    'security_alert', 'moderation'
);
CREATE TYPE user_role AS ENUM ('user', 'moderator', 'support', 'analyst', 'super_admin');
CREATE TYPE moderation_action AS ENUM ('warning', 'restrict', 'suspend', 'ban', 'restore', 'dismiss');

-- =============================================================================
-- USERS
-- Extends auth.users (managed by Supabase Auth)
-- =============================================================================

CREATE TABLE public.users (
    id                  UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email               TEXT NOT NULL,
    role                user_role NOT NULL DEFAULT 'user',
    onboarding_complete BOOLEAN NOT NULL DEFAULT FALSE,
    is_suspended        BOOLEAN NOT NULL DEFAULT FALSE,
    suspend_until       TIMESTAMPTZ,
    is_banned           BOOLEAN NOT NULL DEFAULT FALSE,
    ban_reason          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ         -- soft delete
);

-- Trigger: auto-insert into public.users when auth.users is created
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.users (id, email)
    VALUES (NEW.id, NEW.email);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- =============================================================================
-- PROFILES
-- =============================================================================

CREATE TABLE public.profiles (
    id                  UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    first_name          TEXT NOT NULL DEFAULT '',
    date_of_birth       DATE,                   -- stored server-side, never sent to other clients as-is
    age                 INT GENERATED ALWAYS AS (
                            EXTRACT(YEAR FROM age(CURRENT_DATE, date_of_birth))::INT
                        ) STORED,
    gender              gender NOT NULL DEFAULT 'unspecified',
    bio                 TEXT NOT NULL DEFAULT '' CHECK (char_length(bio) <= 500),
    occupation          TEXT NOT NULL DEFAULT '',
    education           TEXT NOT NULL DEFAULT '',
    height_cm           INT CHECK (height_cm BETWEEN 100 AND 250),
    relationship_intent relationship_intent NOT NULL DEFAULT 'unspecified',
    languages           TEXT[] NOT NULL DEFAULT '{}',
    -- Location stored as PostGIS point; never sent as exact coords to other users
    location            GEOGRAPHY(POINT, 4326),
    last_active_at      TIMESTAMPTZ,
    is_verified         BOOLEAN NOT NULL DEFAULT FALSE,
    profile_complete    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for geo proximity queries
CREATE INDEX idx_profiles_location ON public.profiles USING GIST (location);
CREATE INDEX idx_profiles_age ON public.profiles (age);
CREATE INDEX idx_profiles_gender ON public.profiles (gender);

-- =============================================================================
-- PROFILE PHOTOS
-- =============================================================================

CREATE TABLE public.profile_photos (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id  UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    url         TEXT NOT NULL,
    is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order  SMALLINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Only one primary photo per profile
CREATE UNIQUE INDEX idx_profile_photos_primary
    ON public.profile_photos (profile_id)
    WHERE is_primary = TRUE;

CREATE INDEX idx_profile_photos_profile ON public.profile_photos (profile_id, sort_order);

-- =============================================================================
-- PROFILE PROMPTS
-- =============================================================================

CREATE TABLE public.profile_prompts (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id  UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    question    TEXT NOT NULL,
    answer      TEXT NOT NULL CHECK (char_length(answer) <= 300),
    sort_order  SMALLINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profile_prompts_profile ON public.profile_prompts (profile_id, sort_order);

-- =============================================================================
-- INTERESTS
-- =============================================================================

CREATE TABLE public.interests (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        TEXT NOT NULL UNIQUE,
    emoji       TEXT NOT NULL DEFAULT '',
    category    TEXT NOT NULL DEFAULT 'general',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE public.user_interests (
    user_id     UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    interest_id UUID NOT NULL REFERENCES public.interests(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, interest_id)
);

-- =============================================================================
-- PREFERENCES
-- =============================================================================

CREATE TABLE public.preferences (
    user_id             UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    gender_preference   gender[] NOT NULL DEFAULT '{}',
    min_age             SMALLINT NOT NULL DEFAULT 18 CHECK (min_age >= 18),
    max_age             SMALLINT NOT NULL DEFAULT 99 CHECK (max_age <= 99),
    max_distance_km     INT NOT NULL DEFAULT 100 CHECK (max_distance_km BETWEEN 1 AND 20000),
    show_in_discovery   BOOLEAN NOT NULL DEFAULT TRUE,
    show_distance       BOOLEAN NOT NULL DEFAULT TRUE,
    show_age            BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================================================
-- LIKES & PASSES
-- =============================================================================

CREATE TABLE public.likes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    liker_id        UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    liked_id        UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    is_super_like   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT no_self_like CHECK (liker_id != liked_id),
    CONSTRAINT unique_like UNIQUE (liker_id, liked_id)
);

CREATE INDEX idx_likes_liked_id ON public.likes (liked_id, created_at DESC);
CREATE INDEX idx_likes_liker_id ON public.likes (liker_id, created_at DESC);

CREATE TABLE public.passes (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    passer_id   UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    passed_id   UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT no_self_pass CHECK (passer_id != passed_id),
    CONSTRAINT unique_pass UNIQUE (passer_id, passed_id)
);

CREATE INDEX idx_passes_passer_id ON public.passes (passer_id);

-- =============================================================================
-- MATCHES
-- Created automatically by trigger when a mutual like occurs
-- =============================================================================

CREATE TABLE public.matches (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user1_id        UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    user2_id        UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    conversation_id UUID,                   -- filled after conversation created
    is_unmatched    BOOLEAN NOT NULL DEFAULT FALSE,
    unmatched_by    UUID REFERENCES public.users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT no_self_match CHECK (user1_id != user2_id),
    -- Canonical order: always user1_id < user2_id (prevents duplicates)
    CONSTRAINT ordered_users CHECK (user1_id < user2_id),
    CONSTRAINT unique_match UNIQUE (user1_id, user2_id)
);

CREATE INDEX idx_matches_user1 ON public.matches (user1_id, created_at DESC);
CREATE INDEX idx_matches_user2 ON public.matches (user2_id, created_at DESC);

-- Trigger: create match + conversation on mutual like
CREATE OR REPLACE FUNCTION public.check_mutual_like()
RETURNS TRIGGER AS $$
DECLARE
    v_match_id          UUID;
    v_conversation_id   UUID;
    v_user1_id          UUID;
    v_user2_id          UUID;
BEGIN
    -- Check if the liked person already liked the liker
    IF EXISTS (
        SELECT 1 FROM public.likes
        WHERE liker_id = NEW.liked_id AND liked_id = NEW.liker_id
    ) THEN
        -- Canonical order for unique constraint
        v_user1_id := LEAST(NEW.liker_id, NEW.liked_id);
        v_user2_id := GREATEST(NEW.liker_id, NEW.liked_id);

        -- Idempotent: insert match if it doesn't exist
        INSERT INTO public.matches (user1_id, user2_id)
        VALUES (v_user1_id, v_user2_id)
        ON CONFLICT (user1_id, user2_id) DO NOTHING
        RETURNING id INTO v_match_id;

        IF v_match_id IS NOT NULL THEN
            -- Create the conversation
            INSERT INTO public.conversations (match_id)
            VALUES (v_match_id)
            RETURNING id INTO v_conversation_id;

            -- Link conversation back to match
            UPDATE public.matches
            SET conversation_id = v_conversation_id
            WHERE id = v_match_id;

            -- Add both members to the conversation
            INSERT INTO public.conversation_members (conversation_id, user_id)
            VALUES (v_conversation_id, v_user1_id),
                   (v_conversation_id, v_user2_id);

            -- Queue notifications (handled by Edge Function via pg_net or directly)
            INSERT INTO public.notification_queue (user_id, type, data)
            VALUES
                (v_user1_id, 'new_match', jsonb_build_object('match_id', v_match_id, 'partner_id', v_user2_id)),
                (v_user2_id, 'new_match', jsonb_build_object('match_id', v_match_id, 'partner_id', v_user1_id));
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_like_inserted
    AFTER INSERT ON public.likes
    FOR EACH ROW EXECUTE FUNCTION public.check_mutual_like();

-- =============================================================================
-- CONVERSATIONS & MESSAGES
-- =============================================================================

CREATE TABLE public.conversations (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id    UUID NOT NULL REFERENCES public.matches(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.conversation_members (
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    last_read_at    TIMESTAMPTZ,
    is_archived     BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE public.messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES public.users(id) ON DELETE SET NULL,
    content         TEXT NOT NULL DEFAULT '',
    message_type    message_type NOT NULL DEFAULT 'text',
    attachment_url  TEXT,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation ON public.messages (conversation_id, created_at DESC);
CREATE INDEX idx_messages_sender ON public.messages (sender_id);

-- Trigger: update conversation.updated_at on new message
CREATE OR REPLACE FUNCTION public.update_conversation_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE public.conversations SET updated_at = NOW() WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER on_message_inserted
    AFTER INSERT ON public.messages
    FOR EACH ROW EXECUTE FUNCTION public.update_conversation_timestamp();

-- =============================================================================
-- BLOCKS
-- =============================================================================

CREATE TABLE public.blocks (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    blocker_id  UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    blocked_id  UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT no_self_block CHECK (blocker_id != blocked_id),
    CONSTRAINT unique_block UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX idx_blocks_blocker ON public.blocks (blocker_id);
CREATE INDEX idx_blocks_blocked ON public.blocks (blocked_id);

-- =============================================================================
-- REPORTS
-- =============================================================================

CREATE TABLE public.reports (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_id     UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    reported_id     UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    category        report_category NOT NULL,
    details         TEXT NOT NULL DEFAULT '',
    status          report_status NOT NULL DEFAULT 'pending',
    reviewed_by     UUID REFERENCES public.users(id),
    reviewed_at     TIMESTAMPTZ,
    resolution_note TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reports_status ON public.reports (status, created_at DESC);
CREATE INDEX idx_reports_reported ON public.reports (reported_id);

-- =============================================================================
-- SUBSCRIPTIONS
-- =============================================================================

CREATE TABLE public.subscriptions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    tier            subscription_tier NOT NULL DEFAULT 'free',
    status          subscription_status NOT NULL DEFAULT 'active',
    product_id      TEXT NOT NULL,           -- Google Play product ID
    purchase_token  TEXT NOT NULL,           -- Google Play purchase token
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_subscriptions_active_user
    ON public.subscriptions (user_id)
    WHERE status = 'active';

-- Convenience view: current user subscription tier
CREATE OR REPLACE VIEW public.user_subscription_tier AS
SELECT
    u.id AS user_id,
    COALESCE(s.tier, 'free'::subscription_tier) AS tier,
    COALESCE(s.status, 'active'::subscription_status) AS status,
    s.expires_at
FROM public.users u
LEFT JOIN public.subscriptions s
    ON s.user_id = u.id AND s.status IN ('active', 'grace_period')
ORDER BY s.created_at DESC;

-- =============================================================================
-- NOTIFICATIONS
-- =============================================================================

CREATE TABLE public.notification_queue (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    type        notification_type NOT NULL,
    data        JSONB NOT NULL DEFAULT '{}',
    sent_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.notifications (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    type        notification_type NOT NULL,
    title       TEXT NOT NULL,
    body        TEXT NOT NULL,
    data        JSONB NOT NULL DEFAULT '{}',
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON public.notifications (user_id, created_at DESC);

-- =============================================================================
-- DEVICES (FCM tokens)
-- =============================================================================

CREATE TABLE public.devices (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    fcm_token   TEXT NOT NULL,
    platform    TEXT NOT NULL DEFAULT 'android',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_device_token UNIQUE (fcm_token)
);

-- =============================================================================
-- MODERATION
-- =============================================================================

CREATE TABLE public.moderation_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    target_user_id  UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    moderator_id    UUID NOT NULL REFERENCES public.users(id),
    action          moderation_action NOT NULL,
    reason          TEXT NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_moderation_target ON public.moderation_events (target_user_id, created_at DESC);

-- =============================================================================
-- AUDIT LOG (immutable append-only)
-- =============================================================================

CREATE TABLE public.audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID REFERENCES public.users(id) ON DELETE SET NULL,
    action      TEXT NOT NULL,
    table_name  TEXT,
    record_id   TEXT,
    old_data    JSONB,
    new_data    JSONB,
    ip_address  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON public.audit_logs (user_id, created_at DESC);

-- =============================================================================
-- UPDATED_AT TRIGGERS (applied to tables that need it)
-- =============================================================================

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_users_updated_at
    BEFORE UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER set_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER set_conversations_updated_at
    BEFORE UPDATE ON public.conversations
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER set_reports_updated_at
    BEFORE UPDATE ON public.reports
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER set_subscriptions_updated_at
    BEFORE UPDATE ON public.subscriptions
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- =============================================================================
-- DISCOVERY FUNCTION
-- Returns ranked profiles for a given user within their preferences
-- Never returns exact location; returns approx_distance_km only
-- =============================================================================

CREATE OR REPLACE FUNCTION public.discover_profiles(
    p_user_id       UUID,
    p_limit         INT DEFAULT 20,
    p_offset        INT DEFAULT 0
)
RETURNS TABLE (
    id              UUID,
    first_name      TEXT,
    age             INT,
    gender          gender,
    bio             TEXT,
    occupation      TEXT,
    education       TEXT,
    height_cm       INT,
    relationship_intent relationship_intent,
    languages       TEXT[],
    distance_km     NUMERIC,
    is_verified     BOOLEAN,
    last_active_at  TIMESTAMPTZ
) AS $$
DECLARE
    v_pref          RECORD;
    v_location      GEOGRAPHY;
BEGIN
    -- Load caller's preferences and location
    SELECT p.location, pr.*
    INTO v_location, v_pref
    FROM public.preferences pr
    JOIN public.profiles p ON p.id = pr.user_id
    WHERE pr.user_id = p_user_id;

    RETURN QUERY
    SELECT
        prof.id,
        prof.first_name,
        prof.age,
        prof.gender,
        prof.bio,
        prof.occupation,
        prof.education,
        prof.height_cm,
        prof.relationship_intent,
        prof.languages,
        ROUND((ST_Distance(prof.location::GEOGRAPHY, v_location) / 1000)::NUMERIC, 1) AS distance_km,
        prof.is_verified,
        prof.last_active_at
    FROM public.profiles prof
    JOIN public.users u ON u.id = prof.id
    JOIN public.preferences target_pref ON target_pref.user_id = prof.id
    WHERE
        -- Not the caller
        prof.id != p_user_id
        -- Account must be active
        AND u.is_suspended = FALSE
        AND u.is_banned = FALSE
        AND u.deleted_at IS NULL
        -- Onboarding must be complete
        AND u.onboarding_complete = TRUE
        -- Target shows in discovery
        AND target_pref.show_in_discovery = TRUE
        -- Age range
        AND prof.age BETWEEN v_pref.min_age AND v_pref.max_age
        -- Gender preference (caller's preference)
        AND (v_pref.gender_preference = '{}' OR prof.gender = ANY(v_pref.gender_preference))
        -- Distance
        AND ST_DWithin(
            prof.location::GEOGRAPHY,
            v_location,
            v_pref.max_distance_km * 1000  -- metres
        )
        -- Not already liked or passed
        AND NOT EXISTS (
            SELECT 1 FROM public.likes WHERE liker_id = p_user_id AND liked_id = prof.id
        )
        AND NOT EXISTS (
            SELECT 1 FROM public.passes WHERE passer_id = p_user_id AND passed_id = prof.id
        )
        -- Not blocked in either direction
        AND NOT EXISTS (
            SELECT 1 FROM public.blocks
            WHERE (blocker_id = p_user_id AND blocked_id = prof.id)
               OR (blocker_id = prof.id AND blocked_id = p_user_id)
        )
        -- Must have at least one photo
        AND EXISTS (
            SELECT 1 FROM public.profile_photos WHERE profile_id = prof.id
        )
    ORDER BY
        prof.is_verified DESC,              -- verified profiles first
        prof.last_active_at DESC NULLS LAST, -- recently active
        distance_km ASC                     -- closer first as tie-breaker
    LIMIT p_limit
    OFFSET p_offset;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

-- =============================================================================
-- ROW LEVEL SECURITY
-- =============================================================================

-- Enable RLS on all tables
ALTER TABLE public.users                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profile_photos       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profile_prompts      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.preferences          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.likes                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.passes               ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.matches              ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blocks               ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports              ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscriptions        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices              ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.moderation_events    ENABLE ROW LEVEL SECURITY;

-- ── users ─────────────────────────────────────────────────────────────────────
CREATE POLICY "Users can read own record"
    ON public.users FOR SELECT USING (auth.uid() = id);

CREATE POLICY "Users can update own record"
    ON public.users FOR UPDATE USING (auth.uid() = id);

-- ── profiles ──────────────────────────────────────────────────────────────────
CREATE POLICY "Users can read any non-banned profile"
    ON public.profiles FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.users u
            WHERE u.id = profiles.id AND u.is_banned = FALSE AND u.deleted_at IS NULL
        )
    );

CREATE POLICY "Users can insert own profile"
    ON public.profiles FOR INSERT WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update own profile"
    ON public.profiles FOR UPDATE USING (auth.uid() = id);

-- ── profile_photos ────────────────────────────────────────────────────────────
CREATE POLICY "Anyone can view photos of non-banned users"
    ON public.profile_photos FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.users u
            WHERE u.id = profile_photos.profile_id AND u.is_banned = FALSE
        )
    );

CREATE POLICY "Users can manage own photos"
    ON public.profile_photos FOR ALL USING (auth.uid() = profile_id);

-- ── preferences ───────────────────────────────────────────────────────────────
CREATE POLICY "Users can manage own preferences"
    ON public.preferences FOR ALL USING (auth.uid() = user_id);

-- ── likes ─────────────────────────────────────────────────────────────────────
CREATE POLICY "Users can read likes they sent or received"
    ON public.likes FOR SELECT
    USING (auth.uid() = liker_id OR auth.uid() = liked_id);

CREATE POLICY "Users can insert own likes"
    ON public.likes FOR INSERT WITH CHECK (auth.uid() = liker_id);

CREATE POLICY "Users can delete own likes"
    ON public.likes FOR DELETE USING (auth.uid() = liker_id);

-- ── passes ────────────────────────────────────────────────────────────────────
CREATE POLICY "Users can manage own passes"
    ON public.passes FOR ALL USING (auth.uid() = passer_id);

-- ── matches ───────────────────────────────────────────────────────────────────
CREATE POLICY "Users can read own matches"
    ON public.matches FOR SELECT
    USING (auth.uid() = user1_id OR auth.uid() = user2_id);

-- ── conversations ─────────────────────────────────────────────────────────────
CREATE POLICY "Conversation members can read"
    ON public.conversations FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.conversation_members cm
            WHERE cm.conversation_id = conversations.id AND cm.user_id = auth.uid()
        )
    );

-- ── conversation_members ──────────────────────────────────────────────────────
CREATE POLICY "Members can view own membership"
    ON public.conversation_members FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Members can update own membership"
    ON public.conversation_members FOR UPDATE USING (auth.uid() = user_id);

-- ── messages ──────────────────────────────────────────────────────────────────
CREATE POLICY "Conversation members can read messages"
    ON public.messages FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.conversation_members cm
            WHERE cm.conversation_id = messages.conversation_id AND cm.user_id = auth.uid()
        )
    );

CREATE POLICY "Conversation members can send messages"
    ON public.messages FOR INSERT
    WITH CHECK (
        auth.uid() = sender_id
        AND EXISTS (
            SELECT 1 FROM public.conversation_members cm
            WHERE cm.conversation_id = messages.conversation_id AND cm.user_id = auth.uid()
        )
    );

CREATE POLICY "Senders can soft-delete own messages"
    ON public.messages FOR UPDATE
    USING (auth.uid() = sender_id);

-- ── blocks ────────────────────────────────────────────────────────────────────
CREATE POLICY "Users can manage own blocks"
    ON public.blocks FOR ALL USING (auth.uid() = blocker_id);

-- ── reports ───────────────────────────────────────────────────────────────────
CREATE POLICY "Users can submit reports"
    ON public.reports FOR INSERT WITH CHECK (auth.uid() = reporter_id);

CREATE POLICY "Users can read own reports"
    ON public.reports FOR SELECT USING (auth.uid() = reporter_id);

-- ── subscriptions ─────────────────────────────────────────────────────────────
CREATE POLICY "Users can read own subscriptions"
    ON public.subscriptions FOR SELECT USING (auth.uid() = user_id);

-- ── notifications ─────────────────────────────────────────────────────────────
CREATE POLICY "Users can read own notifications"
    ON public.notifications FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can update own notifications"
    ON public.notifications FOR UPDATE USING (auth.uid() = user_id);

-- ── devices ───────────────────────────────────────────────────────────────────
CREATE POLICY "Users can manage own devices"
    ON public.devices FOR ALL USING (auth.uid() = user_id);

-- =============================================================================
-- SEED: Interests
-- =============================================================================

INSERT INTO public.interests (name, emoji, category) VALUES
('Hiking', '🥾', 'outdoors'),
('Photography', '📷', 'arts'),
('Cooking', '🍳', 'lifestyle'),
('Travelling', '✈️', 'lifestyle'),
('Music', '🎵', 'arts'),
('Reading', '📚', 'lifestyle'),
('Gaming', '🎮', 'tech'),
('Yoga', '🧘', 'fitness'),
('Running', '🏃', 'fitness'),
('Coffee', '☕', 'food'),
('Wine', '🍷', 'food'),
('Movies', '🎬', 'arts'),
('Art', '🎨', 'arts'),
('Dancing', '💃', 'arts'),
('Fitness', '💪', 'fitness'),
('Surfing', '🏄', 'outdoors'),
('Cycling', '🚴', 'outdoors'),
('Tech', '💻', 'tech'),
('Pets', '🐾', 'lifestyle'),
('Volunteering', '🤝', 'community')
ON CONFLICT (name) DO NOTHING;
