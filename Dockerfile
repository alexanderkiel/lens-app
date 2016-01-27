FROM nginx:1.9.9

RUN rm /usr/share/nginx/html/*
COPY resources/public/css /usr/share/nginx/html/css
COPY resources/public/fonts /usr/share/nginx/html/fonts
ADD resources/public/js/compiled/main.js /usr/share/nginx/html/js/compiled/
RUN chmod 644 /usr/share/nginx/html/js/compiled/main.js
COPY resources/public/index.html /usr/share/nginx/html/

COPY docker/add-md5sums.sh /
RUN chmod +x /add-md5sums.sh
RUN /add-md5sums.sh
RUN rm /add-md5sums.sh

COPY docker/nginx.conf /etc/nginx/
COPY docker/start.sh /
RUN chmod +x /start.sh

CMD ["/start.sh"]
