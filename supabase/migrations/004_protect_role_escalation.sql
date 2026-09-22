-- Defense in depth: even though "Admins can update any user" lets any admin-tier
-- role UPDATE the users table (for banning/suspending), only a super_admin should
-- ever be able to change the `role` column itself — otherwise a moderator could
-- promote themselves (or anyone) to super_admin via the same UPDATE path.
--
-- auth.role() = 'service_role' is exempted: the admin-create-user Edge Function
-- uses the service_role key server-side, after its own JS-level check that only
-- a super_admin caller may request an elevated role — this avoids double-blocking
-- that already-trusted, already-checked path.
CREATE OR REPLACE FUNCTION public.protect_role_escalation()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role AND auth.role() <> 'service_role' THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.users
            WHERE id = auth.uid() AND role = 'super_admin' AND is_banned = FALSE
        ) THEN
            RAISE EXCEPTION 'Only super_admin can change a user role';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE TRIGGER protect_role_escalation
    BEFORE UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION public.protect_role_escalation();
