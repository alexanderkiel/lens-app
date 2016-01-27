#!/usr/bin/env bash

# Adds MD5 sums to the lens.css and lens.js

DIR=/usr/share/nginx/html/css
MD5SUM=$(md5sum "$DIR/lens.css" | cut -d ' ' -f1)

mv "$DIR/lens.css" "$DIR/lens-$MD5SUM.css"
sed -i "s/lens.css/lens-$MD5SUM.css/" /usr/share/nginx/html/index.html

DIR=/usr/share/nginx/html/js/compiled
MD5SUM=$(md5sum "$DIR/main.js" | cut -d ' ' -f1)

mv "$DIR/main.js" "$DIR/main-$MD5SUM.js"
sed -i "s/main.js/main-$MD5SUM.js/" /usr/share/nginx/html/index.html
