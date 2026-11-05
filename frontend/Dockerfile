# Multi-Stage Build für Angular Frontend
# Builder läuft immer nativ auf der Build-Plattform (keine QEMU-Emulation),
# das Ergebnis ist statisches JS/HTML und damit plattformunabhängig.
FROM --platform=$BUILDPLATFORM node:22-alpine AS builder
WORKDIR /app

# Der OpenAPI Generator (API-Clients aus openapi/*-api.yaml) laeuft auf Java.
RUN apk add --no-cache openjdk17-jre-headless

COPY package*.json ./
RUN npm ci --legacy-peer-deps

COPY . .
RUN npm run build:prod

# Production: nginx (wird pro Zielplattform gebaut, amd64 und arm64)
FROM nginx:alpine
COPY --from=builder /app/dist/cycling-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80