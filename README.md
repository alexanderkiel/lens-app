__Please start at the [Top-Level Lens Repo][11].__

# Lens App

[![Build Status](https://travis-ci.org/alexanderkiel/lens-app.svg?branch=master)](https://travis-ci.org/alexanderkiel/lens-app)
[![Docker Pulls](https://img.shields.io/docker/pulls/akiel/lens-app.svg)](https://hub.docker.com/r/akiel/lens-app/)

The Lens App is a static ClojureScript frontend single page app using various 
backend services. Please see section [Architecture](#architecture) for more 
information.

## Build

Currently a complete compilation including a production optimized ClojureScript
to JavaScript compilation works using:

    lein with-profile production compile :all

## Test

    lein cljsbuild test

## Run Lens App with Docker

There is an automated build of Lens App on [Docker Hub][10] which builds a
container running a [Nginx][7] web server which delivers the static files of the
Lens App web application and proxies the backend services.

The container exposes port 80 and needs three environment variables

 * `AUTH_HOST` - the hostname of the [Lens Auth Service][5]
 * `WORKBOOK_HOST` - the hostname of the [Lens Workbook Service][8]
 * `WAREHOUSE_HOST` - the hostname of the [Lens Warehouse Service][9]
 * `REPORT_URI` - the URI were reports can be found
 * `ACRF_URI` - the URI were aCRFs can be found

Start the container with the following command

    docker run -p 80:80 -e AUTH_HOST=<...> -e WORKBOOK_HOST=<...> -e WAREHOUSE_HOST=<...> akiel/lens-app

## Develop

Lens App uses [figwheel][6]

    rlwrap lein figwheel

Currently I get many warnings if I start figwheel without doing a `lein clean` 
first. 

You need to have the five backend services of Lens running under the URIs
specified in the `resources/public/index-dev.html`. Currently this is

    var lensAuth = "http://localhost:5001/auth";
    var lensWorkbook = "http://localhost:5002/wb";
    var lensWarehouse = "http://localhost:5003/wh";
    var lensReport = "http://192.168.99.100:3001/report";
    var lensAcrf = "http://192.168.99.100:3000/acrf";

## Architecture

Lens as a system consists of one frontend web application and various backend
services and so follows the style of a [microservice architecture][1].

The frontend application Lens App is build after the principles described in
[Static Apps][2]. Lens App is written in [ClojureScript][3] which is compiled to
JavaScript at build time. After loading Lens App in the browser, it talks to
various backend services through AJAX.

### Authentication and Authorization

Lens backend services use token-based authentication. [Bearer tokens][4] are
used. The tokens are issued by the [Lens Auth][5] service.

http://www.staticapps.org/articles/authentication-and-authorization

### Frontend State Management and DOM Update

TODO: React, Om

## License

Copyright © 2015 Alexander Kiel

Distributed under the Eclipse Public License, the same as Clojure.

[1]: <http://martinfowler.com/articles/microservices.html>
[2]: <http://www.staticapps.org/>
[3]: <https://github.com/clojure/clojurescript>
[4]: <https://tools.ietf.org/html/rfc6750>
[5]: <https://github.com/alexanderkiel/lens-auth>
[6]: <https://github.com/bhauman/lein-figwheel>
[7]: <http://nginx.org/>
[8]: <https://github.com/alexanderkiel/lens-workbook>
[9]: <https://github.com/alexanderkiel/lens-warehouse>
[10]: <https://registry.hub.docker.com/u/akiel/lens-app/>
[11]: <https://github.com/alexanderkiel/lens>
