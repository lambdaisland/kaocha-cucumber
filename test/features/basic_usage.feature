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
                :src-paths           ["src"]
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
    When I run `bin/kaocha`
    Then the output should contain:
      """text
      """
