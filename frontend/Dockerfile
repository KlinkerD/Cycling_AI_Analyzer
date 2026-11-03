# Multi-Stage Build für Angular Frontend
FROM node:20-alpine AS builder
WORKDIR /app

# Der OpenAPI Generator (API-Clients aus openapi/*-api.yaml) laeuft auf Java.
RUN apk add --no-cache openjdk17-jre-headless

COPY package*.json ./
RUN npm ci --legacy-peer-deps

COPY . .
RUN npm run build:prod

# Production: nginx
FROM nginx:alpine
COPY --from=builder /app/dist/cycling-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

