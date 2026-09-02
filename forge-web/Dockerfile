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
COPY --from=build --chown=forge:forge /app/.next/standalone ./
COPY --from=build --chown=forge:forge /app/.next/static ./.next/static
USER forge
EXPOSE 3000
CMD ["node", "server.js"]

