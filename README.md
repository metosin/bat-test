# Boot-alt-test [![Clojars Project](https://img.shields.io/clojars/v/metosin/boot-alt-test.svg)](https://clojars.org/metosin/boot-alt-test)

Fast Clojure.test runner for Boot and Lein.

## Features

- **Requires tools.namespace 0.3.0-alpha3**
- Uses [eftest](https://github.com/weavejester/eftest) to display pretty reports
    - Can optionally run tests parallel
    - Can capture output and display the output just for the failing tests (`:capture-output?`, enabled by default)
    - Can be configured to stop running tests after the first failure (`:fail-fast?`)
- Easy way to setup and combine eftest reporters:
    - Built-in reporters can be referred by keywords `:pretty`, `:progress` and `:junit`
    - Reporter can be map with `:type` (referring to reporter fn) and option `:output-to`
    which will redirect the output to a file.
    - Multiple reporters can be combined when defining them as vector:
    `(alt-test :report [:pretty {:type :json :output-to "target/junit.xml"}])`
- Uses [clojure.tools.namespace](https://github.com/clojure/tools.namespace) to reload
changed namespaces and to run only the tests in changed or affected namespaces
- Tries to recover from namespace reload errors so that no process restart is needed
    - This means that after some exceptions all the namespaces have to reloaded
    - Related: ([CTN-6](http://dev.clojure.org/jira/browse/TNS-6), [CTN-24](http://dev.clojure.org/jira/browse/TNS-24))
- Run all tests by hitting `enter`
- Two hooks to manage the test environment
    - `on-start` hook: run a function before any tests are run
    - `on-end` hook: run a function after all tests are run

![Screenshot](./screenshot.png)

### Leiningen features

- Built-in file change watcher
- Copies lein-test API, e.g. test-selectors:
    - `lein alt-test :only namespace/test-var`
    - `lein alt-test only-this-namespace`
    - `lein alt-test :integration`
- `:notify-command` for calling `notify-send` or Growl or such

## Boot Usage

1. Add `[metosin/boot-alt-test "X.X.X" :scope "test"]` as a dependency in your
  `build.boot`

1. Add `(require '[metosin.boot-alt-test :refer (alt-test)])` somewhere in your
   build.boot to make the task available to your Boot workflow.

1. Run `boot alt-test` at the command-line or `(boot (alt-test))` in the REPL, or add `alt-test` task as part of your Boot pipeline.

See `boot alt-test -h` for a list of available task options.

## Lein Usage

1. Add `[metosin/boot-alt-test "X.X.X"]` as a plugin in your `project.clj`

1. Add options under `:alt-test` key in project map and run `lein alt-test` at the command-line

See `lein alt-test help` for a list of available task options.

## License

Copyright © 2016-2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
