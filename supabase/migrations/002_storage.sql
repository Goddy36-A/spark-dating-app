-- =============================================================================
-- Storage Buckets
-- Run after 001_initial_schema.sql
-- =============================================================================

-- Profile photos bucket (public read, auth write)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'profile-photos',
    'profile-photos',
    TRUE,
    10485760,   -- 10 MB max per file
    ARRAY['image/jpeg', 'image/png', 'image/webp']
) ON CONFLICT (id) DO NOTHING;

-- Message attachments bucket (private — only accessible to conversation members)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'message-attachments',
    'message-attachments',
    FALSE,
    20971520,   -- 20 MB max
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif']
) ON CONFLICT (id) DO NOTHING;

-- ── Storage RLS ───────────────────────────────────────────────────────────────

-- Profile photos: any authenticated user can view; only owner can upload/delete
DROP POLICY IF EXISTS "Anyone can view profile photos" ON storage.objects;
CREATE POLICY "Anyone can view profile photos"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'profile-photos');

DROP POLICY IF EXISTS "Authenticated users can upload own profile photos" ON storage.objects;
CREATE POLICY "Authenticated users can upload own profile photos"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'profile-photos'
        AND auth.role() = 'authenticated'
        -- Path must start with user's UUID
        AND (storage.foldername(name))[1] = auth.uid()::TEXT
    );

DROP POLICY IF EXISTS "Users can delete own profile photos" ON storage.objects;
CREATE POLICY "Users can delete own profile photos"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'profile-photos'
        AND (storage.foldername(name))[1] = auth.uid()::TEXT
    );

-- Message attachments: only conversation members
DROP POLICY IF EXISTS "Conversation members can view attachments" ON storage.objects;
CREATE POLICY "Conversation members can view attachments"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'message-attachments'
        AND auth.role() = 'authenticated'
        -- Folder name is conversation_id; member check
        AND EXISTS (
            SELECT 1 FROM public.conversation_members cm
            WHERE cm.conversation_id = (storage.foldername(name))[1]::UUID
              AND cm.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Conversation members can upload attachments" ON storage.objects;
CREATE POLICY "Conversation members can upload attachments"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'message-attachments'
        AND auth.role() = 'authenticated'
        AND EXISTS (
            SELECT 1 FROM public.conversation_members cm
            WHERE cm.conversation_id = (storage.foldername(name))[1]::UUID
              AND cm.user_id = auth.uid()
        )
    );

