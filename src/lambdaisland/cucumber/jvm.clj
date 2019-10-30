(ns lambdaisland.cucumber.jvm
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [lambdaisland.cucumber.jvm.types :as types])
  (:import [cucumber.api SnippetType Result Result$Type]
           [cucumber.api.event Event]
           [cucumber.runner EventBus]
           [cucumber.runtime Backend BackendSupplier CucumberException FeatureSupplier Glue RuntimeOptions]
           [cucumber.runtime.io Resource ResourceLoader]
           [cucumber.runtime.model CucumberFeature FeatureLoader]
           [cucumber.runtime.snippets Snippet SnippetGenerator]
           [cucumber.util Encoding]
           [clojure.lang IDeref]
           [gherkin AstBuilder Parser TokenMatcher]
           [gherkin.ast GherkinDocument]
           [gherkin.events PickleEvent]
           [io.cucumber.cucumberexpressions Argument Group ParameterType Transformer]
           [io.cucumber.stepexpression TypeRegistry]
           [java.util Locale]
           [java.util List Map]))

(def ^:dynamic ^Glue *glue* nil)
(def ^:dynamic *state* nil)
(def ^:dynamic ^TypeRegistry *type-registry* nil)

(defn camel->kebab [s]
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
           (map (comp camel->kebab key))
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
  [^TypeRegistry registry type string]
  (let [param-type (-> registry
                       (.parameterTypeRegistry)
                       (.lookupByTypeName type))
        group      (Group. string 0 (count string) [])]
    (assert param-type)
    (.getValue (Argument. group param-type))))

(defn register-type! [^TypeRegistry registry {:cucumber.parameter/keys [name
                                                                        regexp
                                                                        class
                                                                        transformer
                                                                        suggest?
                                                                        prefer-for-regexp-match?]
                                              :or {suggest? true
                                                   regexp ""
                                                   prefer-for-regexp-match? false
                                                   class 'java.lang.Object}}]
  (require (symbol (namespace transformer)))
  (let [transformer (resolve transformer)
        klass (Class/forName (str class))]
    (.defineParameterType registry
                          (ParameterType. ^String name
                                          ^String regexp
                                          klass
                                          (reify Transformer
                                            (transform [_ s] (transformer s)))
                                          ^Boolean suggest?
                                          ^Boolean prefer-for-regexp-match?))))

(defn glue-collector []
  (let [glue (atom {})]
    (reify
      Glue
      (addStepDefinition [_ step]
        (swap! glue update :steps conj step))
      (addBeforeHook [_ hook]
        (swap! glue update :before-hooks conj hook))
      (addAfterHook [_ hook]
        (swap! glue update :after-hooks conj hook))
      (addBeforeStepHook [_ hook]
        (swap! glue update :before-step-hooks conj hook))
      (addAfterStepHook [_ hook]
        (swap! glue update :after-step-hooks conj hook))

      IDeref
      (deref [_]
        @glue))))

(def glue-cache (atom {}))

(defn copy-glue [^Glue to from]
  (run! #(.addStepDefinition to %) (:steps from))
  (run! #(.addBeforeHook to %) (:before-hooks from))
  (run! #(.addAfterHook to %) (:after-hooks from))
  (run! #(.addBeforeStepHook to %) (:before-step-hooks from))
  (run! #(.addAfterStepHook to %) (:after-step-hooks from)))

(defn load-glue [path]
  (if-let [glue (get @glue-cache path)]
    (copy-glue *glue* glue)
    (try
      (let [collector (glue-collector)]
        (binding [*glue* collector]
          (when (.exists (io/file path))
            (load-file path)))
        (copy-glue *glue* @collector)
        (swap! glue-cache assoc path @collector))
      (catch Throwable t
        (throw (CucumberException. ^Throwable t))))))

(defn backend [^ResourceLoader resource-loader ^TypeRegistry type-registry]
  (let [clj-snip       (clojure-snippet)
        param-type-reg (.parameterTypeRegistry type-registry)
        snip-gen       (SnippetGenerator. clj-snip param-type-reg)]

    (reify Backend
      (loadGlue [_ glue glue-paths]
        (binding [*glue* glue]
          (doseq [path     glue-paths
                  resource (.resources resource-loader path ".clj")]
            (load-glue (.getPath ^Resource resource)))))

      (buildWorld [_]
        (push-thread-bindings {#'*state* (atom {})}))

      (disposeWorld [_]
        (pop-thread-bindings))

      (getSnippet [_ step keyword _]
        (.getSnippet snip-gen step keyword nil)))))

(defn backend-supplier [resource-loader type-registry]
  (reify BackendSupplier
    (get [_]
      [(backend resource-loader type-registry)])))

(defn runtime-options [opts]
  (let [default (RuntimeOptions. (List/of))]
    (proxy [RuntimeOptions] [(List/of)]
      (^boolean isMultiThreaded []
       (> (.getThreads ^RuntimeOptions this) 1))
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

(defn ^FeatureLoader feature-loader []
  (FeatureLoader. (types/file-resource-loader)))

(defn ^FeatureSupplier feature-supplier [features]
  (reify FeatureSupplier (get [_] features)))

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

(defn runtime ^cucumber.runtime.Runtime [opts]
  (let [registry (:type-registry opts)
        loader (types/file-resource-loader)]
    (run! (partial register-type! registry) (:param-types opts))
    (-> (cucumber.runtime.Runtime/builder)
        (.withRuntimeOptions (runtime-options opts))
        (.withBackendSupplier (backend-supplier loader registry))
        (.withFeatureSupplier (:feature-supplier opts))
        (.withEventBus (:event-bus opts))
        (.build))))

(defn find-features [path]
  (filter #(.exists (io/file (.getPath ^Resource %)))
          (.resources (types/file-resource-loader) path ".feature")))

(defn doc->pickles [path ^GherkinDocument doc]
  (map
   (fn [pickle]
     (PickleEvent. path pickle))
   (.compile (gherkin.pickles.Compiler.) doc)))

(defn parse-resource [^Resource resource]
  (let [parser               (Parser. (AstBuilder.))
        token-matcher        (TokenMatcher.)
        source               (Encoding/readFile resource)
        ^GherkinDocument doc (.parse parser source token-matcher)
        path (.getPath resource)]
    (CucumberFeature. doc (str path) source (doc->pickles path doc))))

(defn parse [path]
  (let [file (io/file path)]
    ;; passing the same argument here for "root" and "file" to circumvent a
    ;; check in the FileResource constructor, which disallows files that aren't
    ;; under the given root. Since we are assuming files on the filesystem only
    ;; at this point (and not classpath resources from e.g. jars) this can only
    ;; serve to get in the way.
    (parse-resource (types/file-resource file file))))

(defn event->type [^Event e]
  (->> e
       class
       .getSimpleName
       camel->kebab
       (keyword "cucumber")))

(defn result->edn [^Result r]
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
