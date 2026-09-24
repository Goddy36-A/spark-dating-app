-- region: free text (city/area) — profiles.education already existed from 001 but was
-- never actually collected by onboarding; reusing it for "level of education" here.
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS region TEXT NOT NULL DEFAULT '';

-- religion is sensitive/special-category data — nullable, no default value forced,
-- and the onboarding UI always offers an explicit "Prefer not to say" choice rather
-- than silently defaulting or requiring an answer.
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS religion TEXT;
