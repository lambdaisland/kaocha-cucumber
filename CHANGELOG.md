# 0.1.84 (2023-04-14 / e6dfb3d)

## Added
* Added documentation for working with step definitions in the REPL.
* Added documentation about Gherkin tag support.

# 0.0-53 (2019-11-04 / 281b7b5)

## Fixed

- Fixed reflection warnings

## Changed

- Updated Clojure and Kaocha to latest versions

# 0.0-46 (2019-02-28 / a6dccb4)

## Added

## Fixed

- Fixed compatiblity with latest cucumber-jvm

## Changed

# 0.0-36 (2018-12-10 / ddb341a)

## Fixed

- Ignore dangling symlinks for features (similar to fix in 0.0-25, but that one was for glue).
- Prevent glue files from being reloaded for every single scenario, providing a good speedup.
- Report file/line of pending scenarios because of missing snippets
- Honor tags on features as metadata
- Auto-require transformer namespace of custom types
- Don't emit a `:pass` event after every scenario, it inflates the assertion count
- Make sure the test result contains result counts, for things like junit-xml

## Changed

- In case of failure the output now contains the file name, line, scenario, and
  step that was currently executing.
- No longer add the scenario as a `*testing-contexts*`, to prevent the docs
  formatter from printing it twice.
- Give tests nicer ids, based on the filename and scenario description

# 0.0-28 (2018-12-05 / 39c6c82)

## Fixed

- Add Kaocha as a dependency, so that cljdoc can analyze kaocha-cucumber

# 0.0-25 (2018-12-05 / dc68f6e)

## Fixed

- Don't try to load dangling symlinks, this prevents issues with emacs tmp files

# 0.0-20 (2018-11-23 / bf77871)

## Fixed

- Report syntax errors as failures, rather than crashing the process

# 0.0-15 (2018-11-14 / e6f43af)

## Added

- Initial implementation