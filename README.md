# CLIMA POLAR

Aplicación web MVC para publicar y administrar trabajos de climatización.

## Ejecución local con Docker

Antes del primer inicio, crear las credenciales privadas del panel:

```bash
cp .env.example .env
```

Editar `.env`, definir claves privadas y diferentes para `DB_PASSWORD` y
`ADMIN_PASSWORD`. Para trabajar mediante `http://localhost`, configurar
temporalmente `SESSION_COOKIE_SECURE=false`; la plantilla usa `true` de forma
segura para producción.
En una base nueva, Docker crea `climapolar_app` como propietario sin permisos
de superusuario y retira la contraseña de red del usuario de inicialización.
Con Docker Desktop iniciado, ejecutar:

```bash
docker compose up --build -d
```

Si se está actualizando una instalación que ya tenía volumen PostgreSQL, crear
primero un respaldo, definir el nuevo `DB_PASSWORD` en `.env` y ejecutar una
vez:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\apply-db-least-privilege.ps1
```

El script recrea solamente el contenedor PostgreSQL para cargar el `.env`
actual, asigna esa contraseña a `climapolar_app` y comprueba por TCP que la
credencial funciona sin imprimirla. Después se puede recrear la aplicación con
`docker compose up --build -d app`. Durante el primer arranque, Flyway prepara
las tablas de medios y configuración que no existían en el esquema legacy y
recién entonces aplica V2; los checksums publicados de V1 y V2 no cambian.

Abrir `http://localhost:8081`. El portfolio está en `/trabajos` y el panel
privado en `/admin/trabajos`.

Para detener los servicios sin borrar los datos:

```bash
docker compose down
```

No usar `docker compose down -v`: la opción `-v` elimina la base y los medios.

## Producción: Cloudflare, Nginx y Docker Compose

La aplicación no debe exponerse directamente a Internet. Por defecto Docker
publica el puerto solamente en `127.0.0.1`; colocar delante un proxy HTTPS
(Caddy, Nginx o el proxy administrado del proveedor) y mantener:

```dotenv
SESSION_COOKIE_SECURE=true
APP_BIND_ADDRESS=127.0.0.1
```

El firewall debe bloquear el acceso externo a `8081`. El proxy público debe
redirigir HTTP a HTTPS y sobrescribir `Host`, `X-Real-IP`, `X-Forwarded-For` y
`X-Forwarded-Proto`. Antes
de entregar, rotar la contraseña administrativa y no compartirla por chat o
correo sin cifrar. Configurar siempre en `CLIMAPOLAR_SECURITY_TRUSTED_PROXIES`
la IP o subred exacta del salto inmediato que la aplicación observa en producción.
Con la red bridge incluida en Compose, Nginx entra por `127.0.0.1:8081` y el
contenedor observa el gateway estable `172.30.0.1`. No usar `0.0.0.0/0`.

La lista CIDR `CLIMAPOLAR_SECURITY_TRUSTED_PROXIES` es utilizada por el rate
limiting. `TOMCAT_INTERNAL_PROXIES_REGEX` es una expresión regular distinta que
Tomcat utiliza para aceptar los forwarded headers y determinar esquema, host e
IP remota. No copiar directamente una lista separada por comas entre ambas.
Verificar después que HSTS esté presente por HTTPS y que los intentos
de acceso registren la IP real del cliente. La zona horaria predeterminada se
controla con `TZ=America/Argentina/Buenos_Aires`.

Hay ejemplos para `climapolar.com.ar` y `www.climapolar.com.ar` en
`deploy/nginx/`. Nginx debe reemplazar `X-Forwarded-For` con `$remote_addr`, no
usar `$proxy_add_x_forwarded_for`: así un visitante no puede insertar direcciones
arbitrarias en la cadena. Si Cloudflare está en modo Proxied, generar
`/etc/nginx/snippets/cloudflare-real-ip.conf` con las redes oficiales vigentes de
Cloudflare y restringir 80/443 a esas redes en el firewall. De ese modo
`CF-Connecting-IP` solo se acepta desde un salto autenticado por su IP de origen.

Variables obligatorias o relevantes del `.env` de producción:

```dotenv
DB_PASSWORD=<generar-en-el-VPS>
ADMIN_USERNAME=<definir-para-el-cliente>
ADMIN_PASSWORD=<generar-en-el-VPS-minimo-12-caracteres>
SESSION_COOKIE_SECURE=true
APP_BIND_ADDRESS=127.0.0.1
APP_PORT=8081
CLIMAPOLAR_SECURITY_TRUSTED_PROXIES=127.0.0.1,::1,172.30.0.1
TOMCAT_INTERNAL_PROXIES_REGEX=127[.]0[.]0[.]1|0:0:0:0:0:0:0:1|::1|172[.]30[.]0[.]1
COMPOSE_BACKEND_SUBNET=172.30.0.0/24
COMPOSE_BACKEND_GATEWAY=172.30.0.1
TZ=America/Argentina/Buenos_Aires
```

Para desarrollo HTTP local se permite cambiar solamente
`SESSION_COOKIE_SECURE=false`. Producción debe conservarlo en `true`.

Para generar una clave criptográficamente aleatoria dentro de `.env`, sin
mostrarla en la consola, ejecutar:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\rotate-admin-password.ps1 -Restart
```

Después abrir `.env` localmente, copiar la clave al gestor de contraseñas del
cliente y cerrar el archivo. `.env` no se incluye en Git ni en el build Docker.

## Respaldos

Con los contenedores en ejecución, crear una copia de PostgreSQL y de todos los
medios con:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1
```

El script guarda un dump verificable y los archivos dentro de `backups/`, que
está excluido de Git. Para mantener consistentes la base y los medios, pausa
automáticamente el contenedor de la aplicación durante la copia y lo reanuda al
terminar, incluso si ocurre un error. Copiar esa carpeta además a un
almacenamiento externo.
Probar periódicamente una restauración en un entorno separado antes de depender
del respaldo en producción.

## Ejecución local alternativa

Requiere Java 17, Maven 3.9+ y PostgreSQL. Crear la base `frio_polar`, ajustar
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_USERNAME` y `ADMIN_PASSWORD`, y
ejecutar `mvn spring-boot:run`.

## Verificación antes de entregar

El upgrade desde el esquema de la primera versión se puede probar sin tocar los
volúmenes del proyecto. El siguiente script levanta un PostgreSQL efímero y lo
elimina al terminar:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-legacy-upgrade.ps1
```

```bash
docker compose build --no-cache app
docker compose up -d --force-recreate
docker compose ps
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

Comprobar Inicio, Trabajos, un detalle, login, alta/edición/borrado y una carga
real desde celular. El build ejecuta la suite de tests; no genera el JAR si una
prueba falla.

## Portfolio administrable

- La API pública paginada está disponible en `GET /api/trabajos` y admite los
  parámetros `categoria`, `destacado`, `page` y `size`.
- Cada publicación admite hasta 20 fotografías JPEG/PNG y 6 videos MP4/WebM.
- Las fotografías se redimensionan y comprimen, con una miniatura adicional
  para las cards.
- Los archivos se guardan fuera de PostgreSQL. En Docker persisten en el
  volumen `media_data`; en local, `UPLOAD_DIR` permite elegir la carpeta.
- La migración inicial convierte las imágenes existentes en medios del feed sin
  borrar ni duplicar los trabajos guardados.
