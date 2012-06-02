# reload-templates

Experimental ring middleware for reloading enlive templates.

This ring middleware automatically reloads enlive templates when their HTML files change.

It requires a patch to enlive, to record the source paths of templates in metadata, so that they can be
introspected by the middleware.

  https://github.com/djpowell/enlive/commit/f2806041dc940ded20776695bcaaf4cec60ee6e2.patch


## Limitations

This is just an experiment.  The patch hasn't been accepted into enlive, and is subject to change.

The patch currently assumes that the source parameter to deftemplate and defsnippet is a string.  It might not be.


## Usage

```clojure
    (-> handler
        (wrap-reload-templates [
            'my.namespace1
            'my.namespace2]))
```

## License

Copyright (C) 2012 David Powell

Distributed under the Eclipse Public License, the same as Clojure.
