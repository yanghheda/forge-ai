FROM node:22.22.0-alpine AS dependencies

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM dependencies AS build
COPY . .
RUN npm run build

FROM node:22.22.0-alpine AS runtime

ENV NODE_ENV=production
WORKDIR /app
RUN addgroup --system --gid 1001 forge && adduser --system --uid 1001 forge
# server 仅使用 Node 内置模块，运行时无需 node_modules。
COPY --from=build --chown=forge:forge /app/dist ./dist
COPY --from=build --chown=forge:forge /app/server ./server
COPY --from=build --chown=forge:forge /app/package.json ./
USER forge
EXPOSE 3000
CMD ["node", "server/web-server.mjs"]