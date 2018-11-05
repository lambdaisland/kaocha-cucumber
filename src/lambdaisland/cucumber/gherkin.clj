(ns lambdaisland.cucumber.gherkin
  (:require [clojure.java.io :as io]
            [lambdaisland.cucumber.jvm :as jvm])
  (:import cucumber.runtime.io.FileResource
           [cucumber.runtime.model CucumberFeature FeatureBuilder]
           [gherkin.ast Background Comment DataTable DocString Examples Feature GherkinDocument Location Scenario ScenarioOutline Step TableCell TableRow Tag]))

(defn parse [path]
  (let [list (java.util.LinkedList.)
        builder (FeatureBuilder. list)
        resource (FileResource/createFileResource (io/file "") (io/file path))]
    (.parse builder resource)
    (first list)))

(defn location [node]
  ^{:type :gherkin/location}
  {:line (some-> node
                 .getLocation
                 .getLine)
   :column (some-> node
                   .getLocation
                   .getColumn)})

(defmulti gherkin->edn class)
(defmulti edn->gherkin (fn [e] (:type e (:type (meta e)))))

(defmethod gherkin->edn :default [o] o)
(defmethod edn->gherkin :default [o] o)

(defmethod gherkin->edn CucumberFeature [feat]
  {:type     :cucumber/feature
   :uri      (.getUri feat)
   :document (gherkin->edn (.getGherkinFeature feat))
   :source   (slurp (.getUri feat))
   ;; source is private without a getter :(
   })

(defmethod edn->gherkin :cucumber/feature [{:keys [uri document source]}]
  (CucumberFeature. (edn->gherkin document) uri (or source "")))

(defmethod gherkin->edn GherkinDocument [doc]
  {:type     :gherkin/document
   :feature  (gherkin->edn (.getFeature doc))
   :comments (mapv gherkin->edn (.getComments doc))})

(defmethod edn->gherkin :gherkin/document [{:keys [feature comments]}]
  (GherkinDocument. (edn->gherkin feature) (map edn->gherkin comments)))

(defmethod gherkin->edn Feature [feat]
  {:type        :gherkin/feature
   :location    (location feat)
   :tags        (mapv gherkin->edn (.getTags feat))
   :language    (.getLanguage feat)
   :keyword     (.getKeyword feat)
   :name        (.getName feat)
   :description (.getDescription feat)
   :children    (mapv gherkin->edn (.getChildren feat))})

(defmethod edn->gherkin :gherkin/feature [{:keys [location tags language keyword name description children]}]
  (Feature. (map edn->gherkin tags)
            (edn->gherkin location)
            language
            keyword
            name
            description
            (map edn->gherkin children)))

(defmethod edn->gherkin :gherkin/location [{:keys [line column]}]
  (Location. line column))

(defmethod gherkin->edn Comment [comm]
  {:type        :gherkin/comment
   :location    (location comm)
   :text        (.getText comm)})

(defmethod edn->gherkin :gherkin/comment [{:keys [location text]}]
  (Comment. (edn->gherkin location) text))

(defmethod gherkin->edn Background [scen]
  {:type        :gherkin/background
   :location    (location scen)
   :keyword     (.getKeyword scen)
   :name        (.getName scen)
   :description (.getDescription scen)
   :steps       (mapv gherkin->edn (.getSteps scen))})

(defmethod edn->gherkin :gherkin/background [{:keys [location keyword name description steps]}]
  (Background. (edn->gherkin location)
               keyword
               name
               description
               (map edn->gherkin steps)))

(defmethod gherkin->edn Scenario [scen]
  {:type        :gherkin/scenario
   :location    (location scen)
   :keyword     (.getKeyword scen)
   :name        (.getName scen)
   :description (.getDescription scen)
   :steps       (mapv gherkin->edn (.getSteps scen))
   :tags        (mapv gherkin->edn (.getTags scen))})

(defmethod edn->gherkin :gherkin/scenario [{:keys [location keyword name description steps tags]}]
  (Scenario. (map edn->gherkin tags)
             (edn->gherkin location)
             keyword
             name
             description
             (map edn->gherkin steps)))

(defmethod gherkin->edn ScenarioOutline [scen]
  {:type        :gherkin/scenario-outline
   :location    (location scen)
   :keyword     (.getKeyword scen)
   :name        (.getName scen)
   :description (.getDescription scen)
   :steps       (mapv gherkin->edn (.getSteps scen))
   :tags        (mapv gherkin->edn (.getTags scen))
   :examples    (mapv gherkin->edn (.getExamples scen))})

(defmethod edn->gherkin :gherkin/scenario-outline [{:keys [location keyword name description steps tags examples]}]
  (ScenarioOutline. (map edn->gherkin tags)
                    (edn->gherkin location)
                    keyword
                    name
                    description
                    (map edn->gherkin steps)
                    (map edn->gherkin examples)))

(defmethod gherkin->edn Examples [ex]
  {:type        :gherkin/examples
   :location    (location ex)
   :tags        (mapv gherkin->edn (.getTags ex))
   :keyword     (.getKeyword ex)
   :name        (.getName ex)
   :description (.getDescription ex)
   :table-header (gherkin->edn (.getTableHeader ex))
   :table-body   (mapv gherkin->edn (.getTableBody ex))})

(defmethod edn->gherkin :gherkin/examples [{:keys [location tags keyword name description table-header table-body]}]
  (Examples. (edn->gherkin location)
             (map edn->gherkin tags)
             keyword
             name
             description
             (edn->gherkin table-header)
             (map edn->gherkin table-body)))

(defmethod gherkin->edn Step [step]
  {:type     :gherkin/step
   :location (location step)
   :keyword  (.getKeyword step)
   :text     (.getText step)
   :argument (gherkin->edn (.getArgument step))})

(defmethod edn->gherkin :gherkin/step [{:keys [location keyword text argument]}]
  (Step. (edn->gherkin location)
         keyword
         text
         (edn->gherkin argument)))

(defmethod gherkin->edn DataTable [table]
  {:type :gherkin/data-table
   :rows (mapv gherkin->edn (.getRows table))})

(defmethod edn->gherkin :gherkin/data-table [table]
  (DataTable. (map edn->gherkin (:rows table))))

(defmethod gherkin->edn TableRow [row]
  (with-meta
    (mapv gherkin->edn (.getCells row))
    {:type :gherkin/table-row
     :location (location row)}))

(defmethod edn->gherkin :gherkin/table-row [row]
  (let [location (edn->gherkin (:location (meta row)))]
    (TableRow. location
               (map #(TableCell. location %) row))))

#_
(defn meta-string [string meta]
  (proxy [java.lang.CharSequence clojure.lang.IMeta] []
    (meta [] meta)
    (charAt [n] (.charAt string n))
    (length [] (.length string))
    (subSequence [s e] (.subSequence string s e))
    (toString [] string)))

(defmethod gherkin->edn TableCell [cell]
  #_{:type :gherkin/table-cell
     :location (location cell)}
  (.getValue cell))

(defmethod gherkin->edn DocString [s]
  {:type         :gherkin/doc-string
   :location     (location s)
   :content      (.getContent s)
   :content-type (.getContentType s)})

(defmethod edn->gherkin :gherkin/doc-string [{:keys [location content content-type]}]
  (DocString. (edn->gherkin location) content-type content))

(defmethod gherkin->edn Tag [t]
  {:type     :gherkin/tag
   :location (location t)
   :name     (.getName t)})

(defmethod edn->gherkin :gherkin/tag [{:keys [location name]}]
  (Tag. (edn->gherkin location) name))

(def scenario? (comp #{:gherkin/scenario :gherkin/scenario-outline} :type))

(defn scenarios [feature]
  (filter scenario? (get-in feature [:document :feature :children])))

(defn dedupe-feature
  "Split a feature up into a number of different featurs, each with a single scenario."
  [feature]
  (map #(update-in feature
                   [:document :feature :children]
                   (partial filter (fn [child]
                                     (or (not (scenario? child)) (= child %)))))
       (scenarios feature)))

(comment
  (= (read-string (slurp (io/file "resources/lambdaisland/gherkin/test_feature.edn")))
     (gherkin->edn (parse "resources/lambdaisland/gherkin/test_feature.feature")))


  (let [x (-> "resources/lambdaisland/gherkin/test_feature.feature"
              parse
              gherkin->edn)
        y (-> x edn->gherkin gherkin->edn)]
    (= x y))


  (->> "resources/lambdaisland/gherkin/test_feature.feature"
       parse
       gherkin->edn
       scenarios
       last
       :tags
       )



  (-> (jvm/feature-loader)
      (.load ["test/features"])
      first
      (.getGherkinFeature)
      gherkin->edn
      )


  (with-open [w (io/writer "resources/lambdaisland/gherkin/test_feature.edn")]
    (binding [*out* w]
      (clojure.pprint/pprint
       (gherkin->edn (parse "resources/lambdaisland/gherkin/test_feature.feature"))
       ))))
