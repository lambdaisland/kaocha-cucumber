Feature: Basic usage of `kaocha-cucumber`

  To start writing Cucumber based tests, you need to

  - Add `kaocha` and `kaocha-cucumber` to your project
  - Create a test suite with `:type` `:kaocha.type/cucumber`
  - Create a file with "steps" using the `kaocha-cucumber` dsl
  - Write a feature file

  Scenario: Setting up a test suite and feature
    Given a file named "tests.edn" with:
      """clojure
      #kaocha/v1
      {:tests [{:id                  :features
                :type                :kaocha.type/cucumber
                :test-paths          ["test/features"]
                :cucumber/glue-paths []}]}
      """
    And a file named "test/features/making_tea.feature" with:
      """feature
      Feature: making tea
        Making tea is great!

        Scenario: making green tea
          Given I have green tea leaves
          And I boild water to 85 degrees
          Then I can make a lovely cup of tea
      """
    When I run `bin/kaocha`
    Then the output should contain:
      """text
      PENDING in making-tea/making-green-tea (test/features/making_tea.feature:4)
      You can implement missing steps with the snippets below:
      (Given "I have green tea leaves" [state]
        ;; Write code here that turns the phrase above into concrete actions
        (pending!))

      (And "I boild water to {int} degrees" [state int1]
        ;; Write code here that turns the phrase above into concrete actions
        (pending!))

      (Then "I can make a lovely cup of tea" [state]
        ;; Write code here that turns the phrase above into concrete actions
        (pending!))
      """

  Scenario: Adding step implementations
    Given a file named "tests.edn" with:
      """clojure
      #kaocha/v1
      {:tests [{:id                  :features
                :type                :kaocha.type/cucumber
                :test-paths          ["test/features"]
                :cucumber/glue-paths ["test/step_definitions"]}]}
      """
    And a file named "test/features/making_tea.feature" with:
      """feature
      Feature: making tea
        Making tea is great!

        Scenario: making green tea
          Given I have green tea leaves
          And I boild water to 85 degrees
          Then I can make a lovely cup of tea
      """
    And a file named "test/step_definitions/tea_steps.clj" with:
      """clojure
      (ns tea-steps
        (:require [clojure.test :refer :all]
                  [lambdaisland.cucumber.dsl :refer :all]))

      (Given "I have ([a-z]+) tea leaves" [state type]
        (assoc state :tea (keyword type)))

      (And "I boild water to {int} degrees" [state degrees]
        (assoc state :water-temp degrees))

      (Then "I can make a lovely cup of tea" [{:keys [tea water-temp] :as state}]
        (is tea)
        (is water-temp)
        (case tea
          :green
          (is (<= 75 water-temp 85))
          :black
          (is (<= 90 water-temp 100)))
        state)
      """
    When I run `bin/kaocha`
    Then the output should contain:
      """text
      1 tests, 3 assertions, 0 failures.
      """
