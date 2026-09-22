-- =============================================================================
-- Admin dashboard RLS policies
-- Lets users with an admin-tier role (moderator/support/analyst/super_admin)
-- read and manage data via the anon key + real login, instead of ever needing
-- the service_role key in the admin dashboard's client-side code.
-- =============================================================================

-- Helper: check if the currently-authenticated user has an admin-tier role.
-- SECURITY DEFINER so it can read public.users without recursing through
-- the RLS policies defined on that same table.
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.users
        WHERE id = auth.uid()
          AND role IN ('moderator', 'support', 'analyst', 'super_admin')
          AND is_banned = FALSE
    );
$$;

-- users: admins can read everyone, and update moderation fields on anyone
CREATE POLICY "Admins can read all users"
    ON public.users FOR SELECT
    USING (public.is_admin());

CREATE POLICY "Admins can update any user"
    ON public.users FOR UPDATE
    USING (public.is_admin());

-- reports: admins can read/update all reports (regular users already have their own-report policies)
CREATE POLICY "Admins can read all reports"
    ON public.reports FOR SELECT
    USING (public.is_admin());

CREATE POLICY "Admins can update any report"
    ON public.reports FOR UPDATE
    USING (public.is_admin());

-- moderation_events: had RLS enabled with zero policies (blocked everyone) — admins need read+insert
CREATE POLICY "Admins can read moderation events"
    ON public.moderation_events FOR SELECT
    USING (public.is_admin());

CREATE POLICY "Admins can insert moderation events"
    ON public.moderation_events FOR INSERT
    WITH CHECK (public.is_admin() AND moderator_id = auth.uid());

-- matches / messages: admins need aggregate counts for the dashboard stat cards
CREATE POLICY "Admins can read all matches"
    ON public.matches FOR SELECT
    USING (public.is_admin());

CREATE POLICY "Admins can read all messages"
    ON public.messages FOR SELECT
    USING (public.is_admin());
