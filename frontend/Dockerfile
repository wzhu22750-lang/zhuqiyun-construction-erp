# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM nginx:1.28-alpine AS runtime
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist/ /usr/share/nginx/html/
COPY THIRD_PARTY_NOTICES.md /usr/share/nginx/html/THIRD_PARTY_NOTICES.md
COPY docs/ /usr/share/nginx/html/docs/
EXPOSE 80
