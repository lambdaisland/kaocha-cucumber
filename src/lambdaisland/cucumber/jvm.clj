(ns lambdaisland.cucumber.jvm
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import cucumber.api.Result$Type
           cucumber.runner.EventBus
           [cucumber.runtime Backend BackendSupplier CucumberException FeatureSupplier RuntimeOptions]
           [cucumber.runtime.io FileResource FileResourceLoader]
           [cucumber.runtime.model CucumberFeature FeatureLoader]
           [cucumber.runtime.snippets Snippet SnippetGenerator]
           [gherkin AstBuilder Parser TokenMatcher]
           [io.cucumber.cucumberexpressions Argument Group ParameterType Transformer]
           io.cucumber.stepexpression.TypeRegistry
           java.util.Locale
           cucumber.runtime.io.Resource))

(def ^:dynamic *glue* nil)
(def ^:dynamic *state* nil)
(def ^:dynamic *type-registry* nil)

(defn camel->kebap [s]
  (str/join "-" (map str/lower-case (str/split s #"(?=[A-Z])"))))

(defn clojure-snippet []
  (reify
    Snippet
    (template [_]
      (str
       "({0} \"{1}\" [{3}]\n"
       "  ;; {4}\n{5}"
       "  (pending!))\n"))
    (arguments [_ argument-types] ;; map from name to type
      (->> (into {} argument-types)
           (map (comp camel->kebap key))
           (cons "state")
           (str/join " ")))
    (tableHint [_]
      "  ;; The last argument is a vector of vectors of strings.\n")
    (escapePattern [_ pattern]
      (str/replace (str pattern) "\"" "\\\""))))

(defn type-registry
  ([]
   (type-registry (Locale/getDefault)))
  ([locale]
   (TypeRegistry. locale)))

(defn parse-type
  "Parse a string based on a type in the registry.

  Provided as a convenience because the way this is hidden away in a tangle of
  classes is ridiculous."
  [registry type string]
  (let [param-type (-> registry
                       (.parameterTypeRegistry)
                       (.lookupByTypeName type))
        group      (Group. string 0 (count string) [])]
    (assert param-type)
    (.getValue (Argument. group param-type))))

(defn register-type! [registry {:cucumber.parameter/keys [name
                                                          regexp
                                                          class
                                                          transformer
                                                          suggest?
                                                          prefer-for-regexp-match?]
                                :or {suggest? true
                                     regexp ""
                                     prefer-for-regexp-match? false
                                     class 'java.lang.Object}}]
  #_(require (symbol (namespace transformer)))
  (let [transformer (resolve transformer)
        klass (Class/forName (str class))]
    (.defineParameterType registry
                          (ParameterType. name
                                          regexp
                                          klass
                                          (reify Transformer
                                            (transform [_ s] (transformer s)))
                                          suggest?
                                          prefer-for-regexp-match?))))

(defn load-script [path]
  (try
    (when (.exists (io/file path))
      (load-file path))
    (catch Throwable t
      (throw (CucumberException. t)))))

(defn backend [resource-loader type-registry]
  (let [clj-snip       (clojure-snippet)
        param-type-reg (.parameterTypeRegistry type-registry)
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

(defn backend-supplier [resource-loader type-registry]
  (reify BackendSupplier
    (get [this]
      [(backend resource-loader type-registry)])))

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
       (:tag-filters opts (.getTagFilters default)))
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

(defn ^FileResourceLoader resource-loader []
  (FileResourceLoader.))

(defn ^FeatureLoader feature-loader []
  (FeatureLoader. (resource-loader)))

(defn ^FeatureSupplier feature-supplier [features]
  (reify FeatureSupplier (get [this] features)))

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
  (let [registry (:type-registry opts)
        loader (resource-loader)]
    (run! (partial register-type! registry) (:param-types opts))
    (.. (cucumber.runtime.Runtime/builder)
        (withRuntimeOptions (runtime-options opts))
        (withBackendSupplier (backend-supplier loader registry))
        (withFeatureSupplier (:feature-supplier opts))
        (withEventBus (:event-bus opts))
        (build))))

(defn find-features [path]
  (filter #(.exists (io/file (.getPath %)))
          (.resources (resource-loader) path ".feature")))

(defn parse-resource [^Resource resource]
  (let [parser               (Parser. (AstBuilder.))
        token-matcher        (TokenMatcher.)
        source               (cucumber.util.Encoding/readFile resource)
        ^GherkinDocument doc (.parse parser source token-matcher)]
    (CucumberFeature. doc (str (.getPath resource)) source)))

(defn parse [path]
  (parse-resource (FileResource/createFileResource (io/file "") (io/file path))))

(defn event->type [^cucumber.api.event.Event e]
  (->> e
       class
       .getSimpleName
       camel->kebap
       (keyword "cucumber")))

(defn result->edn [^cucumber.api.Result r]
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
        type-registry    (type-registry (if-let [loc (:locale opts)]
                                          (Locale/forLanguageTag loc)
                                          (Locale/getDefault)))
        runtime          (runtime (assoc opts
                                         :type-registry type-registry
                                         :feature-supplier feature-supplier
                                         :event-bus event-bus))]
    (binding [*type-registry* type-registry]
      (.run runtime))))
