-- Keep the application connection isolated from cluster-wide superuser
-- privileges. Read the desired application password from the current
-- container environment instead of copying a verifier from an old volume.
\getenv app_password POSTGRES_PASSWORD
\if :{?app_password}
\else
    \echo 'POSTGRES_PASSWORD no está disponible; no se modificaron los roles.'
    \quit 3
\endif

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
        CREATE ROLE postgres WITH SUPERUSER CREATEDB CREATEROLE LOGIN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'climapolar_app') THEN
        CREATE ROLE climapolar_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
$$;

ALTER ROLE climapolar_app
    WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD :'app_password';

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
