(ns kaocha.type.cucumber
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test :as t]
            [kaocha.core-ext :refer :all]
            [kaocha.hierarchy :as hierarchy]
            [kaocha.output :as output]
            [kaocha.report :as report]
            [kaocha.result :as result]
            [kaocha.testable :as testable]
            [kaocha.type :as type]
            [lambdaisland.cucumber.gherkin :as gherkin]
            [lambdaisland.cucumber.jvm :as jvm]
            [slingshot.slingshot :refer [try+]])
  (:import cucumber.api.PickleStepTestStep
           gherkin.ParserException))

(defn gherkin->meta [gherkin]
  (into {:file (:uri gherkin)
         :line (-> gherkin :location :line)}
        (map
         (fn [{:keys [name]}]
           [(keyword (subs name 1)) true]))
        (:tags gherkin)))

(defn path->id [path test-paths]
  (let [tp (first (filter #(.startsWith path %) test-paths))]
    (-> (cond-> path
          tp
          (str/replace (regex (str "^" tp "/?")) ""))
        (str/replace #"/" ".")
        (str/replace #"_" "-")
        (str/replace #" " "_")
        (str/replace #"\.feature$" ""))))

(defn scenario->id [scenario]
  (if-let [name (:name scenario)]
    (-> name
        str/lower-case
        (str/replace #" " "-")
        (str/replace #"[^\w-_]" ""))
    (str "line-" (-> scenario :location :line))))

(defn scenario->testable [feature suite]
  (let [scenario (first (gherkin/scenarios feature))]
    {::testable/type :kaocha.type/cucumber-scenario
     ::testable/id (keyword (path->id (:uri feature) (:kaocha/test-paths suite))
                            (scenario->id scenario))
     ::testable/meta (gherkin->meta scenario)
     ::testable/desc (str "Scenario: " (or (:name scenario) "<no name>"))
     ::feature feature
     ::locale (:cucumber/locale suite)
     ::glue-paths (:cucumber/glue-paths suite)
     ::param-types (:cucumber/parameter-types suite)
     ::file (:uri feature)
     ::line (-> scenario :location :line)}))

(defn feature->testable [document suite]
  (let [feature (-> document :document :feature)]
    {::testable/type :kaocha.type/cucumber-feature
     ::testable/id (keyword (path->id (:uri document) (:kaocha/test-paths suite)))
     ::testable/desc (str "Feature: " (or (:name feature) "<no name>"))
     ::testable/meta (gherkin->meta feature)
     :kaocha.test-plan/tests (map #(scenario->testable % suite) (gherkin/dedupe-feature document))}))

(defmethod testable/-load :kaocha.type/cucumber [testable]
  (let [test-paths (:kaocha/test-paths testable)
        resources  (mapcat jvm/find-features test-paths)
        tests (map (fn [resource]
                     (let [path (.getPath resource)]
                       (try
                         (-> (jvm/parse-resource resource)
                             gherkin/gherkin->edn
                             (feature->testable testable))
                         (catch Throwable e
                           (output/warn "Failed loading " path ": " (.getMessage e))
                           {::testable/type :kaocha.type/cucumber-feature
                            ::testable/id (path->id path)
                            ::testable/desc "<no name>"
                            :kaocha.test-plan/load-error e}))))
                   resources)]
    (assoc testable
           :kaocha.test-plan/tests
           tests)))

(defmethod testable/-run :kaocha.type/cucumber [testable test-plan]
  (t/do-report {:type :begin-test-suite})
  (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)
        testable (-> testable
                     (dissoc :kaocha.test-plan/tests)
                     (assoc :kaocha.result/tests results))]
    (t/do-report {:type :end-test-suite
                  :kaocha/testable testable})
    testable))

(defmethod testable/-run :kaocha.type/cucumber-feature [testable test-plan]
  (type/with-report-counters
    (t/do-report {:type :cucumber/begin-feature})
    (if-let [load-error (:kaocha.test-plan/load-error testable)]
      (do
        (t/do-report {:type                    :error
                      :message                 "Failed to load Cucumber feature."
                      :expected                nil
                      :actual                  load-error
                      :kaocha.result/exception load-error})
        (t/do-report {:type :cucumber/end-feature})
        (assoc testable :kaocha.result/error 1))
      (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)
            testable (-> testable
                         (dissoc :kaocha.test-plan/tests)
                         (assoc :kaocha.result/tests results))]
        (t/do-report {:type :cucumber/end-feature})
        (merge testable {:kaocha.result/count 1} (type/report-count))))))

(defmulti handle-event (fn [_ e] (jvm/event->type e)))

(defmethod handle-event :default [m e]
  (println "UNHANDLED CUCUMBER EVENT")
  (clojure.pprint/pprint m))

(defmethod handle-event :cucumber/test-run-started [m e] m)
(defmethod handle-event :cucumber/test-source-read [m e] m)
(defmethod handle-event :cucumber/test-case-started [m e]
  m)

(defmethod handle-event :cucumber/test-step-started [m e]
  (let [test-step (.testStep e)]
    (when (instance? PickleStepTestStep test-step)
      (push-thread-bindings {#'t/*testing-contexts* (conj t/*testing-contexts* (.getStepText test-step))
                             #'testable/*test-location* {:file (str/join ":" (butlast (str/split (.getStepLocation test-step) #":")))
                                                         :line (.getStepLine test-step)}})))
  m)

(defmethod handle-event :cucumber/test-step-finished [m e]
  (when (instance? PickleStepTestStep (.testStep e))
    (pop-thread-bindings))
  m)

(defmethod handle-event :cucumber/test-case-finished [m e]
  (let [{:keys [status error]} (jvm/result->edn (.result e))]
    (binding [testable/*test-location* {:file (str (.getUri (.testCase e)))
                                        :line (.getLine (.testCase e))}]
      (case status
        :passed
        (do)

        :failed
        (t/do-report {:type :error
                      :actual error})
        :undefined
        (t/do-report {:type :kaocha/pending})

        :pending
        (t/do-report {:type :kaocha/pending})

        (prn status))))
  m)

(defmethod handle-event :cucumber/test-run-finished [m e]
  (update m :done deliver :ok))

(defmethod handle-event :cucumber/snippets-suggested-event [m e]
  ;; (t/do-report {:type :cucumber/snippet-suggested
  ;;               :snippets (.snippets e)
  ;;               :locations (.stepLocations e)})
  (update (merge {::snippets []} m)
          ::snippets
          conj {:snippets (.snippets e)
                :locations (.stepLocations e)}))

(defmethod testable/-run :kaocha.type/cucumber-scenario [testable test-plan]
  (let [{::keys          [feature]
         ::testable/keys [wrap]} testable

        done  (promise)
        state (atom {:done done})
        test  #(jvm/execute!
                {:features    [(gherkin/edn->gherkin feature)]
                 :glue        (::glue-paths testable)
                 :state       state
                 :handler     handle-event
                 :param-types (::param-types testable)
                 :locale      (::locale testable)
                 :monochrome? (not output/*colored-output*)})
        test  (reduce #(%2 %1) test wrap)]
    (type/with-report-counters
      (t/do-report {:type :cucumber/begin-scenario})
      (try+
       (test)
       (catch :kaocha/fail-fast e)
       (catch Throwable e
         (t/do-report {:type                    :error
                       :message                 "Uncaught exception, not in assertion."
                       :expected                nil
                       :actual                  e
                       :kaocha.result/exception e})))
      (when-let [snippets (::snippets @state)]
        (t/do-report {:type     :cucumber/snippets-suggested
                      :snippets snippets
                      :file (::file testable)
                      :line (::line testable) }))
      (t/do-report {:type :cucumber/end-scenario})
      (merge testable
             {:kaocha.result/count 1}
             (type/report-count)))))

(defmethod t/report :cucumber/snippets-suggested [{:keys [snippets] :as m}]
  (println "\nPENDING in" (report/testing-vars-str m))
  (println "You can implement missing steps with the snippets below:");
  ;;(clojure.pprint/pprint snippets)
  (let [scenarios (get-in m [:kaocha/testable
                             ::feature
                             :document
                             :feature
                             :children])
        steps     (mapcat :steps scenarios)]
    (t/with-test-out
      (doseq [snipcol  snippets
              snippet  (:snippets snipcol)
              location (:locations snipcol)
              step     steps
              :let     [line (.getLine location)]]
        (when (= line (-> step :location :line))
          (println (str/replace snippet "**KEYWORD** " (:keyword step))))))))

(s/def :kaocha.type/cucumber (s/keys :req [:kaocha/source-paths
                                           :kaocha/test-paths
                                           :cucumber/glue-paths]
                                     :opt [:cucumber/parameter-types
                                           :cucumber/locale]))

(s/def :kaocha.type/cucumber-feature any?)
(s/def :kaocha.type/cucumber-scenario (s/keys :req [::feature]))

(s/def :cucumber/glue-paths (s/coll-of string?))
(s/def :cucumber/locale? string?)

(s/def :cucumber/parameter-types (s/coll-of :cucumber/parameter-type))
(s/def :cucumber/parameter-type (s/keys :req [:cucumber.parameter/name
                                              :cucumber.parameter/transformer]
                                        :opt [:cucumber.parameter/regexp
                                              :cucumber.parameter/class
                                              :cucumber.parameter/suggest?
                                              :cucumber.parameter/prefer-for-regexp-match?]))

(s/def :cucumber.parameter/name string?)
(s/def :cucumber.parameter/transformer qualified-symbol?)
(s/def :cucumber.parameter/regexp string?)
(s/def :cucumber.parameter/class simple-symbol?)
(s/def :cucumber.parameter/suggest? boolean?)
(s/def :cucumber.parameter/prefer-for-regexp-match? boolean?)

(hierarchy/derive! :cucumber/begin-feature :kaocha/begin-group)
(hierarchy/derive! :cucumber/end-feature :kaocha/end-group)

(hierarchy/derive! :cucumber/begin-scenario :kaocha/begin-test)
(hierarchy/derive! :cucumber/end-scenario :kaocha/end-test)

(hierarchy/derive! :cucumber/snippets-suggested :kaocha/deferred)

(hierarchy/derive! :kaocha.type/cucumber-scenario :kaocha.testable.type/leaf)

(comment
  (require 'kaocha.repl)

  (kaocha.repl/test-plan {:tests [{:id :unit
                                   :type :kaocha.type/cucumber
                                   :kaocha/source-paths ["src"]
                                   :kaocha/test-paths ["test/features"]
                                   :cucumber/glue-paths ["test/features/step_definitions"]}]})

  (kaocha.repl/run-all)

  )
