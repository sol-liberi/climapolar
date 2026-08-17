-- Keep the application connection isolated from cluster-wide superuser
-- privileges. Copy the initial SCRAM verifier to a non-superuser owner and
-- then remove network-password authentication from the bootstrap superuser.
DO $$
DECLARE
    bootstrap_verifier TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
        CREATE ROLE postgres WITH SUPERUSER CREATEDB CREATEROLE LOGIN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'climapolar_app') THEN
        CREATE ROLE climapolar_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;

    SELECT rolpassword INTO bootstrap_verifier
      FROM pg_authid
     WHERE rolname = 'frio_polar';

    IF bootstrap_verifier IS NOT NULL THEN
        EXECUTE format(
            'ALTER ROLE climapolar_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
            bootstrap_verifier
        );
    END IF;
END
$$;

ALTER ROLE postgres PASSWORD NULL;
ALTER DATABASE frio_polar OWNER TO climapolar_app;
ALTER SCHEMA public OWNER TO climapolar_app;
ALTER TABLE IF EXISTS public.trabajos OWNER TO climapolar_app;
ALTER TABLE IF EXISTS public.trabajo_medios OWNER TO climapolar_app;
ALTER TABLE IF EXISTS public.configuracion_aplicacion OWNER TO climapolar_app;
ALTER SEQUENCE IF EXISTS public.trabajos_id_seq OWNER TO climapolar_app;
ALTER SEQUENCE IF EXISTS public.trabajo_medios_id_seq OWNER TO climapolar_app;
GRANT CONNECT ON DATABASE frio_polar TO climapolar_app;
GRANT USAGE, CREATE ON SCHEMA public TO climapolar_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO climapolar_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO climapolar_app;
ALTER DEFAULT PRIVILEGES FOR ROLE climapolar_app IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO climapolar_app;
ALTER DEFAULT PRIVILEGES FOR ROLE climapolar_app IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO climapolar_app;
ALTER ROLE frio_polar PASSWORD NULL;
