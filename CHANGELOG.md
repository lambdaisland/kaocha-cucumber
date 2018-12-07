# Unreleased

## Fixed

- Ignore dangling symlinks for features (similar to fix in 0.0-25, but that one was for glue).
- Prevent glue files from being reloaded for every single scenario, providing a good speedup.
- Report file/line of pending scenarios because of missing snippets
- Honor tags on features as metadata
- Auto-require transformer namespace of custom types

## Changed

- In case of failure the output now contains the file name, line, scenario, and
  step that was currently executing.
- No longer add the scenario as a `*testing-contexts*`, to prevent the docs
  formatter from printing it twice.

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
