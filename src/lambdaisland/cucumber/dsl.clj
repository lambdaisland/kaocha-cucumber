(ns lambdaisland.cucumber.dsl
  (:require [lambdaisland.cucumber.jvm :as jvm])
  (:import [cucumber.api PendingException]
           [cucumber.runtime HookDefinition StepDefinition]
           [cucumber.runtime.filter TagPredicate]
           [io.cucumber.stepexpression ExpressionArgumentMatcher StepExpressionFactory]
           [io.cucumber.datatable DataTable]
           [java.lang.reflect Method]))

(defn expression-factory ^StepExpressionFactory []
  (assert jvm/*type-registry*)
  (StepExpressionFactory. jvm/*type-registry*))

(defn- location-str [{:keys [file line]}]
  (str file ":" line))

(defn add-step-definition [pattern argc fun location]
  (when-let [glue jvm/*glue*]
    (.addStepDefinition
     glue
     (reify
       StepDefinition
       (matchedArguments [_ step]
         (let [expression-factory (expression-factory)
               step-expression    (.createExpression expression-factory pattern)
               argument-matcher   (ExpressionArgumentMatcher. step-expression)]
           (.argumentsFrom argument-matcher step (into-array Class (repeat argc Object)))))
       (getLocation [_ _]
         (location-str location))
       (getParameterCount [_]
         nil)
       (execute [_ args]
         (swap! jvm/*state* #(apply fun % args)))
       (isDefinedAt [_ stack-trace-element]
         (and (= (.getLineNumber stack-trace-element)
                 (:line location))
              (= (.getFileName stack-trace-element)
                 (:file location))))
       (getPattern [_]
         (str pattern))
       (isScenarioScoped [_]
         false)))))

(defmulti add-hook-definition (fn [t & _] t))

(defmethod add-hook-definition :before [_ tag-expression hook-fun location]
  (when-let [glue jvm/*glue*]
    (let [tp (TagPredicate. tag-expression)]
      (.addBeforeHook glue
                      (reify
                        HookDefinition
                        (getLocation [_ _]
                          (location-str location))
                        (execute [_ _]
                          (hook-fun))
                        (matches [_ tags]
                          (.apply tp tags))
                        (getOrder [_] 0)
                        (isScenarioScoped [_]
                          false))))))

(defmethod add-hook-definition :after [_ tag-expression hook-fun location]
  (when-let [glue jvm/*glue*]
    (let [tp (TagPredicate. tag-expression)
          max-parameter-count (->> hook-fun class .getDeclaredMethods
                                   (filter #(= "invoke" (.getName ^Method %)))
                                   (map #(count (.getParameterTypes ^Method %)))
                                   (apply max))]
      (.addAfterHook glue
                     (reify
                       HookDefinition
                       (getLocation [_ _]
                         (location-str location))
                       (execute [_ scenario-result]
                         (if (zero? max-parameter-count)
                           (hook-fun)
                           (hook-fun scenario-result)))
                       (matches [_ tags]
                         (.apply tp tags))
                       (getOrder [_] 0)
                       (isScenarioScoped [_] false))))))

(defmulti coerce-arg type)

(defmethod coerce-arg :default [x] x)

(defmethod coerce-arg DataTable [^DataTable table]
  (into [] (map (partial into [])) (.cells table)))

(defmacro step-macros [& names]
  (cons 'do
        (for [name names]
          `(defmacro ~name {:style/indent [2]} [pattern# binding-form# & body#]
             `(add-step-definition ~pattern#
                                   (count '~binding-form#)
                                   (fn [& ~'args#]
                                     (let [~binding-form# (map coerce-arg ~'args#)
                                           ~'result# (do ~@body#)]
                                       (when (not (map? ~'result#))
                                         (throw (ex-info "State returned from step must be a map"
                                                         {:keyword '~'~name
                                                          :pattern ~pattern#
                                                          :actual ~'result#})))
                                       ~'result#))
                                   '~{:file *file*
                                      :line (:line (meta ~'&form))})))))

(step-macros
 Given When Then And But)

(defn- hook-location [file form]
  {:file file
   :line (:line (meta form))})

(defmacro Before [tags & body]
  `(add-hook-definition :before ~tags (fn [] ~@body) ~(hook-location *file* &form)))

(defmacro After [tags & body]
  `(add-hook-definition :after ~tags (fn [] ~@body) ~(hook-location *file* &form)))

(defn pending! []
  (throw (PendingException.)))
