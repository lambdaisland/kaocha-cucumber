(ns lambdaisland.cucumber.jvm
  (:require [clojure.string :as str])
  (:import cucumber.api.Result$Type
           cucumber.runner.EventBus
           [cucumber.runtime Backend BackendSupplier CucumberException FeatureSupplier RuntimeOptions]
           cucumber.runtime.io.FileResourceLoader
           cucumber.runtime.model.FeatureLoader
           [cucumber.runtime.snippets Snippet SnippetGenerator]
           io.cucumber.stepexpression.TypeRegistry
           java.util.Locale))

(def ^:dynamic *glue* nil)
(def ^:dynamic *state* nil)

(defn camel->kebap [s]
  (str/join "-" (map str/lower-case (str/split s #"(?=[A-Z])"))))

(defn clojure-snippet []
  (reify
    Snippet
    (template [_]
      (str
       "({0} \"{1}\" [{3}]\n"
       "  ;; {4}\n"
       "  (pending!))"))
    (arguments [_ argument-types]
      (->> (into {} argument-types)
           (map key)
           (cons "state")
           (str/join " ")))
    (tableHint [_] nil)
    (escapePattern [_ pattern]
      (str/replace (str pattern) "\"" "\\\""))))

(defn type-registry
  ([]
   (type-registry (Locale/getDefault)))
  ([locale]
   (TypeRegistry. locale)))

(defn load-script [path]
  (try
    (load-file path)
    (catch Throwable t
      (throw (CucumberException. t)))))

(defn backend [resource-loader]
  (let [clj-snip       (clojure-snippet)
        type-reg       (type-registry)
        param-type-reg (.parameterTypeRegistry type-reg)
        snip-gen       (SnippetGenerator. clj-snip param-type-reg)]

    (reify Backend
      (loadGlue [_ glue glue-paths]
        (binding [*glue* glue]
          (doseq [path     glue-paths
                  resource (.resources resource-loader path ".clj")]
            (load-script (.getPath resource)))))

      (buildWorld [this]
        (push-thread-bindings {#'*state* (atom {})}))

      (disposeWorld [this]
        (pop-thread-bindings))

      (getSnippet [this step keyword function-name-generator]
        (.getSnippet snip-gen step keyword nil)))))

(defn backend-supplier [resource-loader]
  (reify BackendSupplier (get [this] [(backend resource-loader)])))

(defn runtime-options [opts]
  (let [default (RuntimeOptions. [])]
    (proxy [RuntimeOptions] [[]]
      (^boolean isMultiThreaded []
       (> (.getThreads this) 1))
      (^List getPluginFormatterNames []
       (:plugin-formatter-names opts (.getPluginFormatterNames default)))
      (^List getPluginSummaryPrinterNames []
       (:plugin-summary-printer-names opts (.getPluginSummaryPrinterNames default)))
      (^List getPluginStepDefinitionReporterNames []
       (:plugin-step-definition-reporter-names opts (.getPluginStepDefinitionReporterNames default)))
      (^List getGlue []
       (:glue opts (.getGlue default)))
      (^boolean isStrict []
       (:strict? opts (.isStrict default)))
      (^boolean isDryRun []
       (:dry-run? opts (.isDryRun default)))
      (^boolean isWip []
       (:wip? opts (.isWip default)))
      (^List getFeaturePaths []
       (:feature-paths opts (.getFeaturePaths default)))
      (^List getNameFilters []
       (:name-filters opts (.getNameFilters default)))
      (^List getTagFilters []
       (:tag-filter opts (.getTagFilters default)))
      (^Map getLineFilters []
       (:line-filters opts (.getLineFilters default)))
      (^boolean isMonochrome []
       (:monochrome? opts (.isMonochrome default)))
      (^SnippetType getSnippetType []
       (:snippet-type opts (.getSnippetType default)))
      (^List getJunitOptions []
       (:junit-options opts (.getJunitOptions default)))
      (^int getThreads []
       (:threads opts (.getThreads default))))))

(defn resource-loader []
  (FileResourceLoader.))

(defn feature-loader []
  (FeatureLoader. (resource-loader)))

(defn feature-supplier [features]
  (reify FeatureSupplier (get [this] features)))

(defn event-bus []
  (let [events (atom [])]
    [events
     (reify EventBus
       (getTime [_]
         (System/nanoTime))
       (send [_ e]
         (swap! events conj e))
       (sendAll [_ es]
         (swap! events into es))
       (registerHandlerFor [_ _ _])
       (removeHandlerFor [_ _ _]))]))

(defn event-adaptor [state handler]
  (reify EventBus
    (getTime [_]
      (System/nanoTime))
    (send [_ e]
      (swap! state handler e))
    (sendAll [_ es]
      (swap! state #(reduce handler % es)))
    (registerHandlerFor [_ _ _])
    (removeHandlerFor [_ _ _])))

(defn runtime [opts]
  (.. (cucumber.runtime.Runtime/builder)
      (withRuntimeOptions (runtime-options opts))
      (withBackendSupplier (backend-supplier (resource-loader)))
      (withFeatureSupplier (:feature-supplier opts))
      (withEventBus (:event-bus opts))
      (build)))

(defn load-features [feature-paths]
  (.load (feature-loader) feature-paths))

(defn event->type [e]
  (->> e
       class
       .getSimpleName
       camel->kebap
       (keyword "cucumber")))

(defn result->edn [r]
  {:status (condp = (.getStatus r)
             Result$Type/PASSED    :passed
             Result$Type/SKIPPED   :skipped
             Result$Type/PENDING   :pending
             Result$Type/UNDEFINED :undefined
             Result$Type/AMBIGUOUS :ambiguous
             Result$Type/FAILED    :failed)
   :duration (.getDuration r)
   :error (.getError r)})

(defn execute! [opts]
  (let [event-bus        (event-adaptor (:state opts) (:handler opts))
        feature-supplier (feature-supplier (:features opts))
        runtime          (runtime (assoc opts
                                         :feature-supplier feature-supplier
                                         :event-bus event-bus))]
    (.run runtime)))

(comment

  (-> (feature-loader)
      (.load ["test/features"])
      (feature-supplier)
      (.get))


  (let [feature (-> (feature-loader)
                    (.load ["test/features"])
                    (feature-supplier)
                    (.get)
                    first)]
    ;; https://www.programcreek.com/java-api-examples/?code=mauriciotogneri/green-coffee/green-coffee-master/greencoffee/src/main/java/gherkin/pickles/Compiler.java

    (.getUri feature)
    (.getChildren (.getFeature (.getGherkinFeature feature)))
    (.getTags (.getFeature (.getGherkinFeature feature)))
    (.getName (first (.getChildren (.getFeature (.getGherkinFeature feature)))))
    (.getName (.getFeature (.getGherkinFeature feature)))
    )
  (run!)

  (let [f (-> (feature-loader)
              (.load ["test/features"])
              first
              (.getGherkinFeature)
              (.getFeature)
              )]
    f
    )

  (execute! {:features (-> (load-features ["test/features"])
                           first
                           lambdaisland.cucumber.gherkin/gherkin->edn
                           lambdaisland.cucumber.gherkin/dedupe-feature
                           second
                           lambdaisland.cucumber.gherkin/edn->gherkin
                           vector)
             :glue ["test/features/step_definitions"]})


  )
