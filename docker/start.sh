#!/usr/bin/env bash

sed -i "s/<lens-auth>/${AUTH_HOST}/" /etc/nginx/nginx.conf
sed -i "s/<lens-workbook>/${WORKBOOK_HOST}/" /etc/nginx/nginx.conf
sed -i "s/<lens-warehouse>/${WAREHOUSE_HOST}/" /etc/nginx/nginx.conf
sed -i "s#<lens-report-uri>#${REPORT_URI}#" /etc/nginx/nginx.conf
sed -i "s#<lens-acrf-uri>#${ACRF_URI}#" /etc/nginx/nginx.conf

nginx -g "daemon off;"
