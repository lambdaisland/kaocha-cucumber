# lambdaisland/kaocha-cucumber

[![CircleCI](https://circleci.com/gh/lambdaisland/kaocha-cucumber.svg?style=svg)](https://circleci.com/gh/lambdaisland/kaocha-cucumber) [![cljdoc badge](https://cljdoc.org/badge/lambdaisland/kaocha-cucumber)](https://cljdoc.org/d/lambdaisland/kaocha-cucumber/CURRENT) [![Clojars Project](https://img.shields.io/clojars/v/lambdaisland/kaocha-cucumber.svg)](https://clojars.org/lambdaisland/kaocha-cucumber)

Kaocha support for Cucumber tests.

[Cucumber](https://cucumber.io) is a "Behavior Driven Development" tool, a way
to write "feature" tests in a human readable format known as Gherkin syntax.

This project adds Cucumber support to [Kaocha](https://github.com/lambdaisland/kaocha), allowing to write and Cucumber tests using Clojure.

## State of `kaocha-cucumber`

This project was developed in the first place to power Kaocha's own integration
tests. It is usable but has not seen a lot of polish or hardening yet. Place
create GitHub issues if you run into problems or things that need improving.

## Getting started

This assumes you already have Kaocha setup, including a `tests.edn` and
`bin/kaocha` wrapper.

Start by adding `lambdaisland/kaocha-cucumber` to your project,

``` clojure
;; deps.edn
{:paths [,,,]
 :deps {,,,}
 :aliases
 {:test {:extra-deps {lambdaisland/kaocha { ,,, }
                      lambdaisland/kaocha-cucumber { ,,, }}}}}
```

Create a directory which will contain your Cucumber tests (`*.feature` files),
and one which will contain step definitions (`*.clj` files).

``` shell
mkdir -p test/features test/feature_steps
```

Now add a Cucumber test suite to `tests.edn`

``` clojure
#kaocha/v1
{:tests [{:id           :unit
          :type         :kaocha.type/clojure.test
          :source-paths ["src"]
          :test-paths   ["test/unit"]}

         {:id                  :features
          :type                :kaocha.type/cucumber
          :source-paths        ["src"]
          :test-paths          ["test/features"]
          :cucumber/glue-paths ["test/feature_steps"]
          :cucumber/locale     "de-DE"  ; optional. Currently only for number
                                        ; format parsing, passed to
                                        ; java.util.Locale/forLanguageTag
          }]}
```

Finally create a file `test/features/coffeeshop.feature` with the following contents

``` feature
Feature: Coffee shop order fulfilment

  The coffee shop contains a counter where people can place orders.

  Background:
    Given the following price list
      | Matcha Latte | 4.00 |
      | Green Tea    | 3.50 |

  Scenario: Getting change
    When I order a Matcha Latte
    And pay with $5.00
    Then I get $1.00 back
```

And run it

``` shell
bin/kaocha features
```

Since the steps aren't implemented yet, Kaocha will tell you what you're
missing.

```
$ bin/kaocha features
[(P)]

PENDING in test.features.coffeeshop/line-10 (test/features/coffeeshop.feature:10)
You can implement missing steps with the snippets below:
(When "I order a Matcha Latte" [state]
  ;; Write code here that turns the phrase above into concrete actions
  (pending!))

(And "pay with ${double}" [state double1]
  ;; Write code here that turns the phrase above into concrete actions
  (pending!))

(Then "I get ${double} back" [state double1]
  ;; Write code here that turns the phrase above into concrete actions
  (pending!))

1 tests, 0 assertions, 1 pending, 0 failures.
```

Create a file `test/feature_steps/coffeeshop_steps.clj`, copy the sample code
snippets over, and add a namespace declaration, pulling in
`lambdaisland.cucumber.dsl` and `clojure.test`.


``` clojure
(ns coffeeshop-steps
  (:require [lambdaisland.cucumber.dsl :refer :all]
            [clojure.test :refer :all]))

(Given "the following price list" [state table]
  (assoc state
         :price-list
         (into {}
               (map (fn [[k v]]
                      [k (Double/parseDouble v)]))
               table)))

(When "I order a (.*)" [state product]
  (update state :order conj product))

(And "pay with ${double}" [{:keys [price-list order] :as state} paid]
  (doseq [product order]
    (is (contains? price-list product)))

  (let [total (apply + (map price-list order))]
    (is (<= total paid))

    (assoc state
           :total total
           :paid paid
           :change (- paid total))))

(Then "I get ${double} back" [{:keys [change] :as state} expected]
  (is (= expected change))
  state)
```

Each [step](https://docs.cucumber.io/cucumber/step-definitions/) is followed by
a pattern, a string which is interpreted as either a [regular
expression](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
or a [Cucumber
expression](https://docs.cucumber.io/cucumber/cucumber-expressions/), and then
followed by an argument list (binding form) and a function body.

The first argument is always the current state. This is a map that is passed
from one step to the next as it is executed. The first step receives an empty
map, the next step receives the return value from the first step, etc.

Any remaining arguments correspond with capturing groups or "output parameters"
in the pattern.

Inside the step definitions you use `clojure.test` style assertions.

## Parameter Types

Data tables will be converted to a vector of vectors, other types will be passed
on as-is.

To implement [Custom Parameter
Types](https://docs.cucumber.io/cucumber/cucumber-expressions/#custom-parameter-types),
add a `:cucumber/parameter-types` to your config.

``` clojure
#kaocha/v1
{:tests [{:id :cukes
          :type :kaocha.type/cucumber
          :source-paths ["src"]
          :test-paths ["test/features" "test/support"]
          :cucumber/glue-paths ["test/step_definitions"]
          :cucumber/parameter-types
          [#:cucumber.parameter
           {:name "color"
            :regexp "red|blue|yellow|green"
            :class kaocha.cucumber.extra_types.Color
            :transformer kaocha.cucumber.extra-types/parse-color}]}]}
```

Following keys are understood

``` clojure
:cucumber.parameter/name                     ;;=> string?
:cucumber.parameter/transformer              ;;=> qualified-symbol?
:cucumber.parameter/regexp                   ;;=> string?
:cucumber.parameter/class                    ;;=> simple-symbol?
:cucumber.parameter/suggest?                 ;;=> boolean?
:cucumber.parameter/prefer-for-regexp-match? ;;=> boolean?
```

## Relationship with cucumber-jvm-clojure / lein-cucumber

The existing `cucumber-jvm-clojure` and `lein-cucumber` are based on a very old
version of `cucumber-jvm`, and appear to be unmaintained. Because of this
`kaocha-cucumber` is built directly on top of `cucumber-jvm`. Some of the code
in `lambdaisland.cucumber.jvm` and `lambdaisland.cucumber.dsl` is adapted from
these earlier projects.

If you are migrating existing code bases then note that the DSL syntax is
different. Patterns are given as strings, not regular expressions, the first
"state" argument is a new introduction, and certain argument types are coerced
to Clojure types now.

If you have a code base of significant size based on these legacy projects then
please open a ticket, we might be able to create a separate legacy DSL namespace
that's compatible with old tests.

## License

Copyright &copy; 2018 Arne Brasseur

Available under the terms of the Eclipse Public License 1.0, see LICENSE.txt
