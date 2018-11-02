(ns kaocha.type.cucumber
  (:import [cucumber.runner EventBus]
           [cucumber.runtime Backend BackendSupplier FeatureSupplier RuntimeOptions]
           [cucumber.runtime.io FileResourceLoader]
           [cucumber.runtime.model FeatureLoader]))

(defn backend [resource-loader]
  (reify Backend
    (loadGlue [_ a-glue glue-paths]
      (reset! glue a-glue)
      (doseq [path glue-paths
              resource (.resources resource-loader path ".clj")]
        (binding [*ns* (create-ns 'cucumber.runtime.clj)]
          (load-script (.getPath resource)))))

    (buildWorld [this])

    (disposeWorld [this])

    (getSnippet [this step keyword function-name-generator]
      (.getSnippet snippet-generator step keyword nil))))

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
       (removeHandlerFor [_ _ _])
       )]))



(defn runtime [opts]
  (.. (cucumber.runtime.Runtime/builder)
      (withRuntimeOptions (runtime-options opts))
      (withBackendSupplier (backend-supplier (resource-loader)))
      (withFeatureSupplier (:feature-supplier opts))
      (withEventBus (:event-bus opts))
      (build)))

(-> (feature-loader)
    (.load ["test/features"])
    (feature-supplier)
    (.get))

(let [[events event-bus] (event-bus)
      feature-supplier (-> (feature-loader)
                           (.load ["test/features"])
                           (feature-supplier))
      runtime (runtime {:feature-supplier feature-supplier
                        :glue ["test/features"]
                        :event-bus event-bus})]
  (.run runtime)
  events
  )

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
