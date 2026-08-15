# FRIO POLAR

Aplicación web MVC para publicar y administrar trabajos de climatización.

## Ejecución recomendada con Docker

Con Docker Desktop iniciado, ejecutar:

```bash
docker compose up --build -d
```

Abrir `http://localhost:8081` y el panel en `http://localhost:8081/admin/trabajos`.

Para detener los servicios sin borrar los datos:

```bash
docker compose down
```

## Ejecución local alternativa

Requiere Java 17, Maven 3.9+ y PostgreSQL. Crear la base `frio_polar`, ajustar
`DB_URL`, `DB_USERNAME` y `DB_PASSWORD`, y ejecutar `mvn spring-boot:run`.

Hibernate crea/actualiza la tabla y la aplicación carga cinco ejemplos solamente si no existen trabajos.
