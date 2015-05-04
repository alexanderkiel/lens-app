# Lens App

Lens is a tool for online analytical data processing in medical studies.

The Lens application is a static ClojureScript frontend single page app using 
various backend services. Please see section [Architecture](#architecture) for
more information.

## Usage

* lein with-profile production-run trampoline run

## Usage on Heroku Compatible PaaS

This application uses the following environment vars:

* `PORT` - the port to listen on

## Build

Currently a complete compilation including a production optimized ClojureScript
to JavaScript compilation works using:

    lein with-profile production compile :all

## Run

Just use the command from the Procfiles web task which currently is

    lein with-profile production-run trampoline run

Trampoline lets the Leiningen process behind. So this is a production ready run
command.

If you have foreman installed you can create an `.env` file listing the
environment vars specified above and just type `foreman start`.

## Develop

Lens uses figwheel

    rlwrap lein figwheel

Currently I get many warnings if I start figwheel without doing a `lein clean` 
first.

## Architecture

Lens as a system consists of one frontend application and various backend
services. As such it is a [service oriented architecture][1] (SOA).

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

[1]: <http://en.wikipedia.org/wiki/Service-oriented_architecture>
[2]: <http://www.staticapps.org/>
[3]: <https://github.com/clojure/clojurescript>
[4]: <https://tools.ietf.org/html/rfc6750>
[5]: <https://github.com/alexanderkiel/lens-auth>
