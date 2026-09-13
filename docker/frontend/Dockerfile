FROM node:24.12-alpine AS build

WORKDIR /app

COPY ["Client Side/umo-web-frontend/package.json", "./package.json"]
COPY ["Client Side/umo-web-frontend/package-lock.json", "./package-lock.json"]
RUN npm ci

COPY ["Client Side/umo-web-frontend/", "./"]

ARG VITE_ADMIN_PATH=/secret-admin
ENV VITE_ADMIN_PATH=$VITE_ADMIN_PATH
RUN npm run build

FROM nginx:1.29-alpine

RUN mkdir -p /etc/nginx/umoweb /var/log/umoweb

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/frontend/entrypoint.sh /usr/local/bin/umoweb-entrypoint
COPY --from=build /app/dist /usr/share/nginx/html

RUN chmod 0755 /usr/local/bin/umoweb-entrypoint

ENTRYPOINT ["umoweb-entrypoint"]
CMD ["nginx", "-g", "daemon off;"]

EXPOSE 80
