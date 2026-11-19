# permission-service

Permisos por snippet (`OWNER` / `READ`). Solo lo llama el Snippet Service.

Parte de Snippet Searcher. Contrato de la API: `contracts/permission-service.openapi.yaml` en el repo de documentación.

## Requisitos

- [Nix](https://nixos.org) con flakes. El dev shell trae JDK 21, Gradle, docker-compose y utilidades.
- Contenedores: el dev shell trae Podman rootless y lo deja listo para `docker-compose` y Testcontainers (`DOCKER_HOST`), sin instalar nada en el sistema ni correr como root. Imágenes y contenedores quedan en `.podman/` (ignorado por git); para borrar todo: `podman unshare rm -rf .podman`. Ver `dev/podman-env.sh`.
- Opcional: [direnv](https://direnv.net) para entrar al dev shell y cargar `.env` al hacer `cd` (`direnv allow` la primera vez).

## Uso

```sh
nix develop                # o direnv
cp .env.example .env       # completar los valores de Auth0
./gradlew check            # ktlint + tests (los de integración levantan Postgres/Redis con Podman)
./gradlew ktlintFormat     # arreglar el formato
./gradlew bootRun          # levanta en :$PORT; /health responde sin token
```

El sistema completo se levanta desde `snippet-searcher-infra` con docker compose.

## Variables de entorno

| Variable | Para qué |
|---|---|
| `AUTH0_ISSUER_URI` | Issuer de Auth0, con la barra final |
| `AUTH0_AUDIENCE` | Audience de la API en Auth0 |
| `PORT` | Puerto HTTP (default 8080) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Postgres de este servicio |
| `LOG_FORMAT` | `json` (default: un objeto por línea, con `correlationId`, `user` y `service`) o `plain` (legible, para desarrollo local) |
| `METRICS_PORT` | Puerto interno de `/prometheus` (default 9464; `0` lo apaga). No pasa por NGINX |
| `NEW_RELIC_ENABLED` | `true` prende el agente Java de New Relic y el envío de métricas por OTLP (default `false`) |
| `NEW_RELIC_LICENSE_KEY` | License key (ingest) de New Relic; obligatoria con `NEW_RELIC_ENABLED=true` |
| `NEW_RELIC_OTLP_URL` | Opcional: endpoint OTLP de métricas (default `https://otlp.nr-data.net/v1/metrics`; en la región EU, `https://otlp.eu01.nr-data.net/v1/metrics`) |

## Qué trae la plantilla

- `common/security`: todo exige un JWT válido de Auth0 salvo `/health` (y `/prometheus` en el puerto interno).
- `common/logging`: `X-Correlation-Id` y el usuario (`sub`) en el MDC de cada línea de log.
- `common/observability`: el puerto interno de `/prometheus` (ver "Observabilidad").
- `common/http`: los `RestClient` reenvían el correlation id y el token del usuario a otros servicios.
- `common/error`: `NotFoundException`, `ForbiddenException`, `ConflictException` y errores de validación como Problem Details.
- Migraciones con Flyway en `src/main/resources/db/migration` (`V1__descripcion.sql`, ...). Hibernate solo valida el esquema.

## Observabilidad

- **Logs.** JSON por defecto (formato Logstash de Spring Boot, `logback-spring.xml`): cada línea trae `@timestamp`, `level`, `message`, `service` y el MDC (`correlationId` y `user`). `LOG_FORMAT=plain ./gradlew bootRun` vuelve a la línea legible de siempre; los tests usan `plain`.
- **Correlation ID.** `X-Correlation-Id` (o uno nuevo) en el MDC de cada request, devuelto en la respuesta y reenviado si llama a otro servicio (`common/http`).
- **Métricas** (Micrometer), con el tag `service`. Latencia y tráfico: `http.server.requests` (con histogramas, para percentiles entre réplicas) y `http.client.requests` (llamadas a otros servicios). Errores: los mismos, por `status` y `outcome`. Saturación: `jvm.*` (memoria, GC, threads), `process.cpu.usage`, `system.load.average.1m` y el pool de Postgres (`hikaricp.connections.*`: activas, pendientes).
- **`/prometheus`.** En el puerto interno `METRICS_PORT` (9464) responde sin token, para un scraper dentro de la red de Docker. En el puerto principal se rechaza siempre (`401`/`403`), así que tampoco se llega como `/api/prometheus` por NGINX. `/health` sigue igual en el puerto principal. (Tomcat atiende en los dos puertos; en el 9464 todo lo demás sigue pidiendo JWT.)
- **New Relic** (apagado hasta tener la license key). Con `NEW_RELIC_ENABLED=true` y `NEW_RELIC_LICENSE_KEY`:
  - la imagen carga el agente Java (`docker-entrypoint.sh`, agente 9.5.0 en `/app/newrelic`): APM (las cuatro señales doradas de HTTP, JVM y llamadas externas), distributed tracing y reenvío de los logs con su MDC (`NEW_RELIC_APPLICATION_LOGGING_FORWARDING_CONTEXT_DATA_ENABLED=true`). Se configura por variables `NEW_RELIC_*` (nombre de la app: `NEW_RELIC_APP_NAME`, por defecto el del servicio);
  - Micrometer exporta todas las métricas de arriba por OTLP (`management.otlp.metrics.*`, temporalidad delta, cada 60 s) con el header `api-key`, incluidas las propias que el agente no ve.
  - Para prenderlo: crear la license key (*Ingest - License*) en New Relic y pasar las dos variables al contenedor (compose o `docker secret` en Swarm). Fuera de Docker (`bootRun`) solo se prende el export OTLP; el agente se puede sumar con `-javaagent`.

## Ramas y CI

- `dev` y `prod`. Se trabaja en ramas cortas con pull request a `dev`; `prod` recibe merges desde `dev`.
- `ci.yml`: `./gradlew check` en cada pull request y push.
- `publish.yml`: en cada push a `dev` o `prod`, publica la imagen en GHCR como `ghcr.io/<owner>/permission-service:<rama>` y `:<rama>-<sha>`.

## Migrar a GitHub

```sh
gh repo create <owner>/permission-service --private --source . --remote origin
git push -u origin dev prod
gh repo edit <owner>/permission-service --default-branch dev
```

CI usa el `GITHUB_TOKEN` del workflow para publicar en GHCR. Después de publicar dispara el despliegue de la rama en `snippet-searcher-infra`, con el secreto `DEPLOY_PAT` (token clásico con el scope `repo`). En Swarm, cada variable secreta llega como archivo: `VAR_FILE=/run/secrets/x` (`docker-entrypoint.sh`).
