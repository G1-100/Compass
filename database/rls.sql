-- Enable Row Level Security on every table so the public Supabase anon key
-- cannot read or write anything. The Java servlet connects as the postgres
-- role, which bypasses RLS, so it is unaffected. No policies are added on purpose.
DO $$
DECLARE t record;
BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
  END LOOP;
END $$;
