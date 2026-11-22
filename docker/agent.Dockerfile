# El asistente del chat (agent/): Node con Strands Agents y Gemini. Atiende /api/agent detrás de
# Caddy (docker/Caddyfile); no publica puertos hacia afuera.
#
# Node 24 corre el TypeScript directo, sacándole los tipos, así que no hay paso de compilación: en
# la imagen van solo las dependencias de producción (sin typescript ni prettier) y src/.
FROM node:24-alpine
WORKDIR /app

# pnpm en la versión que fija `packageManager` de agent/package.json.
RUN corepack enable

# Primero solo las dependencias: mientras no cambie el lockfile, esta capa queda en caché.
COPY agent/package.json agent/pnpm-lock.yaml agent/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile --prod

COPY agent/src ./src

# Sin root: el proceso no necesita escribir nada en disco.
USER node
EXPOSE 8085
CMD ["node", "src/main.ts"]
