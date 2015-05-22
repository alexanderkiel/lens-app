# Lens App

Lens is a tool for online analytical data processing in medical studies.

The Lens application is a static ClojureScript frontend single page app using 
various backend services. Please see section [Architecture](#architecture) for
more information.

## Build

Currently a complete compilation including a production optimized ClojureScript
to JavaScript compilation works using:

    lein with-profile production compile :all

## Build a Docker Container

This project contains a Dockerfile which builds a container running a [Nginx][7]
web server which delivers the static files of the Lens App web application and
proxies the backend services.

You have to run `lein with-profile production compile :all` before building the
container. Building work with

    docker build -t lens-app .

The container exposes port 80 and needs three environment variables

 * `AUTH_HOST` - the hostname of the [Lens Auth Service][8]
 * `WORKBOOK_HOST` - the hostname of the [Lens Workbook Service][9]
 * `WAREHOUSE_HOST` - the hostname of the [Lens Warehouse Service][10]

Start the container with the following command

    docker run -p 80:80 -e AUTH_HOST=<...> -e WORKBOOK_HOST=<...> -e WAREHOUSE_HOST=<...> lens-app

## Develop

Lens uses [figwheel][6]

    rlwrap lein figwheel

Currently I get many warnings if I start figwheel without doing a `lein clean` 
first. 

You need to have the three backend services of Lens running under the URIs
specified in the `resources/public/index-dev.html`. Currently this is

    var lensAuth = "http://localhost:5001/auth";
    var lensWorkbook = "http://localhost:5002/wb";
    var lensWarehouse = "http://localhost:5003/wh";

You can run the services by checking them out, creating an `.env` file listing
the appropiate port and starting them with `foreman start`. 

## Architecture

Lens as a system consists of one frontend web application and various backend
services and so follows the principle of a [microservice architecture][1].

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

[1]: <http://martinfowler.com/articles/microservices.html>
[2]: <http://www.staticapps.org/>
[3]: <https://github.com/clojure/clojurescript>
[4]: <https://tools.ietf.org/html/rfc6750>
[5]: <https://github.com/alexanderkiel/lens-auth>
[6]: <https://github.com/bhauman/lein-figwheel>
[7]: <http://nginx.org/>
[8]: <https://github.com/alexanderkiel/lens-auth>
[9]: <https://github.com/alexanderkiel/lens-workbook>
[10]: <https://github.com/alexanderkiel/lens-warehouse>
