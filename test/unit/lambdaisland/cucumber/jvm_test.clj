(ns lambdaisland.cucumber.jvm-test
  (:require [clojure.test :refer :all]
            [lambdaisland.cucumber.gherkin :as gherkin]
            [lambdaisland.cucumber.jvm :as jvm])
  (:import cucumber.api.event.Event
           cucumber.api.SnippetType
           [cucumber.runtime Backend CucumberException]
           cucumber.runtime.io.FileResourceLoader
           cucumber.runtime.model.FeatureLoader
           [gherkin.ast Feature Location]
           io.cucumber.stepexpression.TypeRegistry
           java.io.File
           java.util.Locale))

(deftest camel->kepab-test
  (is (= "foo-bar-baz"
         (jvm/camel->kebap "FooBarBaz")))

  (is (= "foo-bar"
         (jvm/camel->kebap "fooBar"))))

(deftest clojure-snippet-test
  (testing "template"
    (is (= "({0} \"{1}\" [{3}]\n  ;; {4}\n{5}  (pending!))\n"
           (.template (jvm/clojure-snippet)))))

  (testing "arguments"
    (is (= "state"
           (.arguments (jvm/clojure-snippet) {})))

    (is (= "state string int"
           (.arguments (jvm/clojure-snippet) (doto (java.util.LinkedHashMap.)
                                               (.put "string" String)
                                               (.put "int" Integer))))))

  (testing "formatting"
    (is (= "(When \"I got {int} \\\"tomatoes\\\"\" [state string int]
  ;; Write code here
  ;; The last argument is a vector of vectors of strings.
  (pending!))
"
           (let [snippet       (jvm/clojure-snippet)
                 keyword       "When"
                 expression    "I got {int} \"tomatoes\""
                 function-name "unused"
                 arguments     (doto (java.util.LinkedHashMap.)
                                 (.put "string" String)
                                 (.put "int" Integer))]
             ;; Simulating wath SnippetGenerator does
             (java.text.MessageFormat/format
              (.template snippet)
              (into-array Object [keyword
                                  (.escapePattern snippet expression)
                                  function-name
                                  (.arguments snippet arguments)
                                  "Write code here"
                                  (.tableHint snippet)])))))))

(deftest type-registry-test
  (is (instance? TypeRegistry (jvm/type-registry)))

  (testing "adheres to the locale"
    (is (= 234.0 (jvm/parse-type (jvm/type-registry Locale/US) "double" "2,34")))
    (is (= 2.34 (jvm/parse-type (jvm/type-registry Locale/GERMANY) "double" "2,34")))))

(defn transform-color [color]
  {:color color})

(deftest register-type-test
  (let [registry (jvm/type-registry)]
    (jvm/register-type! registry
                        #:cucumber.parameter
                        {:name "color"
                         :transformer `transform-color})
    (is (= {:color "blue"}
           (jvm/parse-type registry "color" "blue")))))

(deftest load-glue-test
  (testing "converts load errors into cucumber exceptions"
    (let [tmpfile (File/createTempFile "bad_clojure" "clj")]
      (spit tmpfile "{:foo}")
      (is (thrown? CucumberException (jvm/load-glue (str tmpfile)))))))

(deftest backend-test
  (let [resource-loader (jvm/resource-loader)
        type-registry   (jvm/type-registry)]
    (testing "it creates a Backend"
      (is (instance? Backend (jvm/backend resource-loader type-registry))))))

(deftest backend-supplier-test
  (let [resource-loader  (jvm/resource-loader)
        type-registry    (jvm/type-registry)
        backend-supplier (jvm/backend-supplier resource-loader type-registry)]
    (testing "it returns a single backend"
      (is (= 1 (count (.get backend-supplier))))
      (is (instance? Backend (first (.get backend-supplier)))))))

(deftest runtime-options-test
  (let [default (jvm/runtime-options {})]
    (testing "isMultiThreaded"
      (is (false? (.isMultiThreaded default)))
      (is (false? (.isMultiThreaded (jvm/runtime-options {:threads 1}))))
      (is (true? (.isMultiThreaded (jvm/runtime-options {:threads 4})))))

    (testing "getPluginFormatterNames"
      (is (= ["progress"]
             (.getPluginFormatterNames default)))

      (is (= ["name"]
             (.getPluginFormatterNames
              (jvm/runtime-options
               {:plugin-formatter-names ["name"]})))))

    (testing "getPluginSummaryPrinterNames"
      (is (= ["default_summary"]
             (.getPluginSummaryPrinterNames default)))

      (is (= ["my_summary"]
             (.getPluginSummaryPrinterNames
              (jvm/runtime-options
               {:plugin-summary-printer-names ["my_summary"]})))))

    (testing "getPluginStepDefinitionReporterNames"
      (is (= []
             (.getPluginStepDefinitionReporterNames default)))

      (is (= ["a_reporter"]
             (.getPluginStepDefinitionReporterNames
              (jvm/runtime-options
               {:plugin-step-definition-reporter-names ["a_reporter"]})))))

    (testing "getGlue"
      (is (= []
             (.getGlue default)))

      (is (= ["glue_path_1"]
             (.getGlue
              (jvm/runtime-options
               {:glue ["glue_path_1"]})))))

    (testing "isStrict"
      (is (false? (.isStrict default)))

      (is (true? (.isStrict (jvm/runtime-options {:strict? true})))))

    (testing "isDryRun"
      (is (false? (.isDryRun default)))

      (is (true? (.isDryRun (jvm/runtime-options {:dry-run? true})))))

    (testing "isWip"
      (is (false? (.isWip default)))

      (is (true? (.isWip (jvm/runtime-options {:wip? true})))))

    (testing "getFeaturePaths"
      (is (= [] (.getFeaturePaths default)))

      (is (= ["a_feat_path"]
             (.getFeaturePaths
              (jvm/runtime-options
               {:feature-paths ["a_feat_path"]})))))

    (testing "getNameFilters"
      (is (= [] (.getNameFilters default)))

      (is (= ["name_filter"]
             (.getNameFilters
              (jvm/runtime-options
               {:name-filters ["name_filter"]})))))

    (testing "getTagFilters"
      (is (= [] (.getTagFilters default)))

      (is (= ["tag_filter"]
             (.getTagFilters
              (jvm/runtime-options
               {:tag-filters ["tag_filter"]})))))

    (testing "getLineFilters"
      (is (= {}
             (.getLineFilters default)))

      (is (= {:foo :bar}
             (.getLineFilters
              (jvm/runtime-options
               {:line-filters {:foo :bar}})))))

    (testing "isMonochrome"
      (is (false? (.isMonochrome default)))

      (is (true? (.isMonochrome (jvm/runtime-options {:monochrome? true})))))

    (testing "getSnippetType"
      (is (= cucumber.api.SnippetType/UNDERSCORE
             (.getSnippetType default)))

      (is (= cucumber.api.SnippetType/CAMELCASE
             (.getSnippetType
              (jvm/runtime-options
               {:snippet-type cucumber.api.SnippetType/CAMELCASE})))))

    (testing "getJunitOptions"
      (is (= [] (.getJunitOptions default))))

    (testing "getThreads"
      (is (= 1 (.getThreads default)))

      (is (= 4 (.getThreads (jvm/runtime-options {:threads 4})))))))

(deftest resource-loader-test
  (is (instance? FileResourceLoader (jvm/resource-loader))))

(deftest feature-loader-test
  (is (instance? FeatureLoader (jvm/feature-loader))))

(deftest feature-supplier-test
  (let [feature (Feature. [] (Location. 0 0) "en" "kw" "name" "desc" [])]
    (is (identical? feature
                    (-> (jvm/feature-supplier [feature])
                        .get
                        first)))))

(deftest event-adapter-test
  (let [state (atom {:total 0})
        handler (fn [state event]
                  (prn state event)
                  (update state :total + (.getTimeStamp event)))
        bus   (jvm/event-adaptor state handler)]
    (is (< (- (.getTime bus) (System/nanoTime)) 200000))

    (.send bus (reify Event (getTimeStamp [_] 3)))
    (.send bus (reify Event (getTimeStamp [_] 4)))

    (is (= 7 (:total @state)))))

(deftest runtime-test
  (let [opts {:type-registry (jvm/type-registry)
              :param-types []
              :feature-supplier (jvm/feature-supplier [])
              :event-bus (jvm/event-adaptor (atom nil) (fn [s e] s))
              :glue ["glue_path"]}]
    ;; As usual everything here is private. Just checking that it least it
    ;; does not blow up.
    (is (instance? cucumber.runtime.Runtime (jvm/runtime opts)))))

(deftest load-features-test
  (is (= "Is it Friday yet?"
         (-> (jvm/parse "resources/lambdaisland/gherkin/test_feature.feature")
             gherkin/gherkin->edn
             :document
             :feature
             :name))))

(deftest event->type-test
  (is (= :cucumber/test-case-started
         (jvm/event->type (cucumber.api.event.TestCaseStarted. 0 nil)))))

(deftest result->edn-test
  (is (= {:status :passed, :duration 111, :error nil}
         (jvm/result->edn
          (cucumber.api.Result. cucumber.api.Result$Type/PASSED 111 nil))))

  (is (= {:status :skipped, :duration 123, :error nil}
         (jvm/result->edn
          (cucumber.api.Result. cucumber.api.Result$Type/SKIPPED 123 nil))))

  (is (= {:status :pending, :duration 135, :error nil}
         (jvm/result->edn
          (cucumber.api.Result. cucumber.api.Result$Type/PENDING 135 nil))))

  (is (= {:status :undefined, :duration 135, :error nil}
         (jvm/result->edn
          (cucumber.api.Result. cucumber.api.Result$Type/UNDEFINED 135 nil))))

  (is (= {:status :ambiguous, :duration 135, :error nil}
         (jvm/result->edn
          (cucumber.api.Result. cucumber.api.Result$Type/AMBIGUOUS 135 nil))))

  (let [error (Exception. "oops")]
    (is (= {:status :failed, :duration 456, :error error}
           (jvm/result->edn
            (cucumber.api.Result. cucumber.api.Result$Type/FAILED 456 error))))))

(deftest execute!-test
  (is (= [:cucumber/test-run-started
          :cucumber/test-source-read
          :cucumber/snippets-suggested-event
          :cucumber/snippets-suggested-event
          :cucumber/snippets-suggested-event
          :cucumber/snippets-suggested-event
          :cucumber/snippets-suggested-event
          :cucumber/snippets-suggested-event
          :cucumber/test-case-started
          :cucumber/test-step-started
          :cucumber/test-step-finished
          :cucumber/test-step-started
          :cucumber/test-step-finished
          :cucumber/test-step-started
          :cucumber/test-step-finished
          :cucumber/test-step-started
          :cucumber/test-step-finished
          :cucumber/test-step-started
          :cucumber/test-step-finished
          :cucumber/test-step-started
          :cucumber/test-step-finished
          :cucumber/test-case-finished
          :cucumber/test-run-finished]
         (let [state (atom [])
               handler (fn [s e] (conj s e))
               features [(->> (jvm/find-features "resources/lambdaisland/gherkin")
                              (map jvm/parse-resource)
                              (map gherkin/gherkin->edn)
                              (mapcat gherkin/dedupe-feature)
                              first)]
               opts {:features (map gherkin/edn->gherkin features)
                     :state state
                     :handler handler}]
           (jvm/execute! opts)
           (map jvm/event->type @state)))))

(comment
  (require 'kaocha.repl)
  (kaocha.repl/run 'lambdaisland.cucumber.jvm-test)
  )
