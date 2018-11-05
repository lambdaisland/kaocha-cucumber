(ns kaocha.type.cucumber
  (:require [kaocha.core-ext :refer :all]
            [clojure.spec.alpha :as s]
            [kaocha.type.ns :as type.ns]
            [kaocha.testable :as testable]
            [kaocha.classpath :as classpath]
            [kaocha.load :as load]
            [clojure.java.io :as io]
            [clojure.test :as t]
            [lambdaisland.cucumber.jvm :as jvm]
            [lambdaisland.cucumber.gherkin :as gherkin]
            [clojure.string :as str]
            [kaocha.hierarchy :as hierarchy]
            [kaocha.result :as result]
            [kaocha.type :as type]
            [clojure.walk :as walk]
            [kaocha.report :as report]
            [slingshot.slingshot :refer [try+ throw+]]))

(defn scenario->testable [feature suite]
  (let [scenario (first (gherkin/scenarios feature))]
    {::testable/type :kaocha.type/cucumber-scenario
     ::testable/id (keyword (-> (:uri feature)
                                (str/replace #"/" ".")
                                (str/replace #" " "_")
                                (str/replace #"\.feature$" ""))
                            (str "line-" (-> scenario :location :line)))
     ::testable/meta (into {:file (:uri feature)
                            :line (-> scenario :location :line)}
                           (map
                            (fn [{:keys [name]}]
                              [(keyword (subs name 1)) true]))
                           (:tags scenario))
     ::testable/desc (or (:name scenario) "<no name>")
     ::feature feature
     ::glue-paths (:cucumber/glue-paths suite)}))

(defn feature->testable [feature suite]
  {::testable/type :kaocha.type/cucumber-feature
   ::testable/id (-> (:uri feature)
                     (str/replace #"/" ".")
                     (str/replace #" " "_")
                     (str/replace #"\.feature$" "")
                     keyword)
   ::testable/desc (or (get-in feature [:document :feature :name]) "<no name>")
   :kaocha.test-plan/tests (map #(scenario->testable % suite) (gherkin/dedupe-feature feature))})

(defmethod testable/-load :kaocha.type/cucumber [testable]
  (let [{:kaocha/keys [test-paths]} testable]
    (assoc testable
           :kaocha.test-plan/tests
           (map (comp #(feature->testable % testable) gherkin/gherkin->edn) (jvm/load-features test-paths)))))

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
  (t/do-report {:type :cucumber/begin-feature})
  (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)
        testable (-> testable
                     (dissoc :kaocha.test-plan/tests)
                     (assoc :kaocha.result/tests results))]
    (t/do-report {:type :cucumber/end-feature
                  :kaocha/testable testable})
    testable))

(defmulti handle-event (fn [_ e] (jvm/event->type e)))

(defmethod handle-event :default [m e] m)

(defmethod handle-event :cucumber/test-run-started [m e] m)
(defmethod handle-event :cucumber/test-source-read [m e] m)
(defmethod handle-event :cucumber/test-case-started [m e] m)
(defmethod handle-event :cucumber/test-step-started [m e] m)
(defmethod handle-event :cucumber/test-step-finished [m e] m)
(defmethod handle-event :cucumber/test-case-finished [m e]
  (let [{:keys [status error]} (jvm/result->edn (.result e))]
    (case status
      :passed
      (t/do-report {:type :pass})
      :failed
      (t/do-report {:type :error
                    :actual error})
      :undefined
      (t/do-report {:type :kaocha/pending})

      :pending
      (t/do-report {:type :kaocha/pending})

      (prn status)))
  m)

(defmethod handle-event :cucumber/test-run-finished [m e]
  (update m :done deliver :ok))

(defmethod handle-event :cucumber/snippets-suggested-event [m e]
  ;; (t/do-report {:type :cucumber/snippet-suggested
  ;;               :snippets (.snippets e)
  ;;               :locations (.stepLocations e)})
  (update (merge {::snippets []} m) ::snippets conj {:snippets (.snippets e)
                                                     :locations (.stepLocations e)}))

(defmethod testable/-run :kaocha.type/cucumber-scenario [testable test-plan]
  (let [{::keys [feature]} testable
        done               (promise)
        state              (atom {:done done})]
    (type/with-report-counters
      (t/do-report {:type :cucumber/begin-scenario})
      (try+
       (jvm/execute! {:features [(gherkin/edn->gherkin feature)]
                      :glue     (::glue-paths testable)
                      :state    state
                      :handler  handle-event})
       (catch :kaocha/fail-fast e)
       (catch Throwable e
         (t/do-report {:type :error
                       :message "Uncaught exception, not in assertion."
                       :expected nil
                       :actual e
                       :kaocha.result/exception e})))
      (when-let [snippets (::snippets @state)]
        (t/do-report {:type :cucumber/snippets-suggested
                      :snippets snippets}))
      (t/do-report {:type :cucumber/end-scenario})
      (merge testable
             {:kaocha.result/count 1}
             (type/report-count)))))

(s/def :kaocha.type/cucumber (s/keys :req [:kaocha/source-paths
                                           :kaocha/test-paths
                                           :cucumber/glue-paths]))

(s/def :kaocha.type/cucumber-feature any?)
(s/def :kaocha.type/cucumber-scenario (s/keys :reg [::feature]))


(hierarchy/derive! :cucumber/begin-feature :kaocha/begin-group)
(hierarchy/derive! :cucumber/end-feature :kaocha/end-group)

(hierarchy/derive! :cucumber/begin-scenario :kaocha/begin-test)
(hierarchy/derive! :cucumber/end-scenario :kaocha/end-test)

(hierarchy/derive! :cucumber/snippets-suggested :kaocha/deferred)

(derive :kaocha.type/cucumber-scenario :kaocha.testable.type/leaf)

(defmethod t/report :cucumber/snippets-suggested [{:keys [snippets] :as m}]
  (println "\nPENDING in" (report/testing-vars-str m))
  (println "You can implement missing steps with the snippets below:");
  (let [scenarios (gherkin/scenarios (get-in m [:kaocha/testable ::feature]))
        steps     (mapcat :steps scenarios)]
    (t/with-test-out
      (doseq [snipcol snippets
              snippet  (:snippets snipcol)
              location (:locations snipcol)
              step     steps
              :let     [line (.getLine location)]]
        (when (= line (-> step :location :line))
          (println (str/replace snippet "**KEYWORD** " (:keyword step))))))))

(comment
  (require 'kaocha.repl)

  (kaocha.repl/test-plan {:tests [{:id :unit
                                   :type :kaocha.type/cucumber
                                   :kaocha/source-paths ["src"]
                                   :kaocha/test-paths ["test/features"]
                                   :cucumber/glue-paths ["test/features/step_definitions"]}]})

  (kaocha.repl/run-all)

  )
