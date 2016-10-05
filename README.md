# Reuters Code Challenge -- Medal Count


## Overview

A user-sortable chart of Olympic medals won by various countries


## Setup

Make sure you have [Leiningen](http://leiningen.org/) installed. Then:

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).

To clean all compiled files:

    lein clean

To create a production build run:

    lein do clean, cljsbuild once min

And open your browser in `resources/public/index.html`.

## Code Run-Through

The app is written in ClojureScript, directly using React via JavaScript interop.

It consists of three namespaces:

1. `reuters.react-wrapper`, which wraps the aforementioned interop, and is inspired by Lesson 6 in the [Lambda Island](https://lambdaisland.com/) lesson series,

2. `reuters.medal-count`, the main namespace, which contains one function meant for public consumption: `place-widget!`, and

3. `reuters.core`, which exists simply to run the `reuters.medal-count/place-widget!` function.

In `reuters.medal-count`, all state -- which consists of the downloaded medal data and the currently selected sort criterion -- is stored in a global atom called `app-state`. This atom is updated by two updating functions, one of which gets run as a callback at the end of the Ajax call to fetch the data, and the other of which gets passed into the React components as a callback. No local state is stored in the components.

### Libraries used:

1. __React.js__ -- for organizing the rendering of components, and for the rendering speed afforded by its virtual DOM

2. __cljs-ajax__ -- a thin wrapper around the Google Closure's XhrIo module, which provides a lot of convenience in comparison to using XhrIo (or the browser's native XHR object) directly.

3. __sablono__ -- a simple macro which converts Hiccup-style html into React elements. Provides convenience when writing a lot of html inside of React components.

4. __Figwheel__ (dev only) -- a development tool which allows live reloading of code, without having to reload the data each time. (The project file was generated from a template provided by Figwheel.)
