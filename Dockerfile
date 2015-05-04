FROM nginx

RUN rm /usr/share/nginx/html/*
COPY resources/public/css /usr/share/nginx/html/css
COPY resources/public/fonts /usr/share/nginx/html/fonts
COPY resources/public/js /usr/share/nginx/html/js
COPY resources/public/index.html /usr/share/nginx/html/

COPY docker/nginx.conf /etc/nginx/
COPY docker/default.conf /etc/nginx/conf.d/
