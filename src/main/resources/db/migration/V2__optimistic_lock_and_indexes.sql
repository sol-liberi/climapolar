ALTER TABLE trabajos
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_trabajos_fecha_id
    ON trabajos (fecha DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_trabajos_categoria_fecha_id
    ON trabajos (tipo_servicio, fecha DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_trabajos_destacados_fecha_id
    ON trabajos (fecha DESC, id DESC)
    WHERE destacado = TRUE;

CREATE INDEX IF NOT EXISTS idx_trabajo_medios_trabajo_orden
    ON trabajo_medios (trabajo_id, orden, id);
