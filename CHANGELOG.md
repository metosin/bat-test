## 0.4.4 (2019-11-08)

- Update Eftest to `0.5.9`, thanks to [Ivan Kuznetsov](https://github.com/Jeiwan). ([#36](https://github.com/metosin/bat-test/pull/36))

**[compare](https://github.com/metosin/boot-alt-test/compare/0.4.3...0.4.4)**

## 0.4.3 (2019-05-14)

- Fix file watch stoppage as a result of compile failures, thanks to [Tommi Kyntola](https://github.com/Kynde). ([#34](https://github.com/metosin/bat-test/issues/34))
- Update Eftest to `0.5.7` and Cloverage to `1.1.1`

**[compare](https://github.com/metosin/boot-alt-test/compare/0.4.2...0.4.3)**

## 0.4.2 (2018-12-14)

- Update Eftest to `0.5.4`

**[compare](https://github.com/metosin/boot-alt-test/compare/0.4.1...0.4.2)**

## 0.4.1 (2018-12-14)

- Update Eftest to `0.5.3`

**[compare](https://github.com/metosin/boot-alt-test/compare/0.4.0...0.4.1)**

## 0.4.0 (2018-02-14)

- Renamed to **Bat-test**
    - Since this release includes a Leiningen plugin, this project isn't boot-only anymore.
      The old name *boot-alt-test* felt misleading, so we decided to rename the project.
      There should not be API breakage beyond the renamings.
    - Artifact-name `metosin/boot-alt-test` -> `metosin/bat-test`
    - Boot namespace `metosin.boot-alt-test` -> `metosin.bat-test`
    - Boot task name `alt-test` -> `bat-test`
    - Leiningen task name `alt-test` -> `bat-test`
- Includes Leiningen plugin
    - Test selector support
    - `:notify-command` option support
- Cloverage support
- Update Eftest to version `0.4.3`

**[compare](https://github.com/metosin/boot-alt-test/compare/0.3.2...0.4.0)**

## 0.3.3 (14.2.2018)

- 0.3.2 with rename notice

## 0.3.2 (27.4.2017)

- Update Eftest to version `0.3.1`

**[compare](https://github.com/metosin/boot-alt-test/compare/0.3.1...0.3.2)**

## 0.3.1 (30.3.2017)

- Add `filter` option to provide function to filter test vars

**[compare](https://github.com/metosin/boot-alt-test/compare/0.3.0...0.3.1)**

## 0.3.0 (19.1.2017)

- Update eftest to version `0.1.2`
- Remove `fail` option and always throw exception. The stacktrace is hidden when using Boot version 2.7.0 or later.

**[compare](https://github.com/metosin/boot-alt-test/compare/0.2.1...0.3.0)**

## 0.2.1 (1.12.2016)

- Only reload already loaded and test ns.
    - This should fix problems in case there are namespaces that are
    unloadable in test pod, e.g. they require `boot.core`. This might also
    improve performance a bit as unncessary namespaces are not loaded.

**[compare](https://github.com/metosin/boot-alt-test/compare/0.2.0...0.2.1)**

## 0.2.0 (1.12.2016)

- Default `parallel` off
- Add hooks `on-start` and `on-end`, these are run before and after all the tests

**[compare](https://github.com/metosin/boot-alt-test/compare/0.1.2...0.2.0)**

## 0.1.2 (22.8.2016)

- Two fixes related to running tests when hitting `Enter`

**[compare](https://github.com/metosin/boot-alt-test/compare/0.1.1...0.1.2)**

## 0.1.1 (21.8.2016)

- Run all tests by pressing `Enter`

**[compare](https://github.com/metosin/boot-alt-test/compare/0.1.0...0.1.1)**

## 0.1.0 (23.5.2016)

- Initial release
