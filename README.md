# Boot-alt-test [![Clojars Project](https://img.shields.io/clojars/v/metosin/boot-alt-test.svg)](https://clojars.org/metosin/boot-alt-test)

Fast Clojure.test runner for Boot.

## Features

- **Requires tools.namespace 0.3.0-alpha3**
- Uses [eftest](https://github.com/weavejester/eftest) to display pretty reports
- When used with Boot watch task, uses [clojure.tools.namespace](https://github.com/clojure/tools.namespace) to reload
changed namespaces and to run only the tests in changed or affected namespaces
- When fails the next tasks are not run but no exception is shown
- Option to throw an exception when tests fail (use on CI to exit process with failure)
- Tries to recover from namespace reload errors so that no process restart is needed
    - This means that after some exceptions all the namespaces have to reloaded
    - Related: ([CTN-6](http://dev.clojure.org/jira/browse/TNS-6), [CTN-24](http://dev.clojure.org/jira/browse/TNS-24))
- Run all tests by hitting `enter`
- Can optionally run tests parallel
- Two hooks to manage test environment
    - `on-start` hook: run a function before any tests are run
    - `on-end` hook: run a function after all tests are run

![Screenshot](./screenshot.png)

## Usage

1. Add `[metosin/boot-alt-test "X.X.X" :scope "test"]` as a dependency in your
  build.boot.

1. Add `(require '[metosin.boot-alt-test :refer (alt-test)])` somewhere in your
   build.boot to make the task available to your Boot workflow.

1. Run `boot alt-test` at the command-line or `(boot (alt-test))` in the REPL, or add `alt-test` task as part of your Boot pipeline.

See `boot alt-test -h` for a list of available task options.

### Test result reporter

Boot-alt-test uses [eftest](https://github.com/weavejester/eftest) and by default that uses a test reporter that displays a progress bar of the test run. Your test output can mess this progress bar so you might want to change the used reporter. This can be achieved by providing `report` option and using alternative `pretty` reporter:

```
# From CLI
$ boot alt-test --report eftest.report.pretty/report

;; From Clojure
(alt-test :report 'eftest.report.pretty/report)
```

## License

Copyright © 2016-2017 Juho Teperi

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
