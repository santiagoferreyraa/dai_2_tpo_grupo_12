# El front compilado, servido por Caddy, que además es la única puerta de entrada al sistema:
# reparte /api entre los módulos (docker/Caddyfile) y, con un dominio, saca el certificado HTTPS.

# --- Compilación -------------------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /app

# pnpm en la versión que fija `packageManager`, sin instalarlo a mano.
RUN corepack enable

# Primero solo las dependencias: mientras no cambie el lockfile, esta capa queda en caché.
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/ .
RUN pnpm build

# --- Servidor ----------------------------------------------------------------------------------
FROM caddy:2.10-alpine
COPY docker/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
