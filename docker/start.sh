#!/usr/bin/env bash

sed -i "s/<lens-auth>/${AUTH_HOST}/" /etc/nginx/conf.d/default.conf
sed -i "s/<lens-workbook>/${WORKBOOK_HOST}/" /etc/nginx/conf.d/default.conf
sed -i "s/<lens-warehouse>/${WAREHOUSE_HOST}/" /etc/nginx/conf.d/default.conf

nginx -g "daemon off;"
