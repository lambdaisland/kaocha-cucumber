(ns lambdaisland.cucumber.output
  "Output gherkin as markdown"
  (:require [lambdaisland.cucumber.jvm :as jvm]
            [lambdaisland.cucumber.gherkin :as gherkin]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- poor-hiccup
  "A poor man's hiccup. Does no escaping or sanitizing at the moment."
  [h]
  (let [render-attrs
        (fn [attrs]
          (->> attrs
               (map (fn [[k v]]
                      (str " " (name k) "='" v "'")))
               (apply str)))]
    (if (string? h)
      h
      (let [[tag attrs] h
            attrs (if (map? attrs) attrs)
            children (if attrs
                       (drop 2 h)
                       (drop 1 h))]
        (str "<" (name tag) (render-attrs attrs) ">"
             (apply str (map poor-hiccup children))
             "</" (name tag) ">")))))

(defmulti print-markdown :type)

(defmethod print-markdown :cucumber/feature [{:keys [document]}]
  (print-markdown document))

(defmethod print-markdown :gherkin/document [{:keys [feature]}]
  (print-markdown feature))

(defmethod print-markdown :gherkin/feature [{:keys [name description children]}]
  (println "#" name)
  (println)
  (when (seq description)
    (println (str/trim description)))
  (println)
  (run! print-markdown children))

(defmethod print-markdown :gherkin/background [{:keys [keyword name steps]}]
  (println "##" (str keyword ":") name)
  (println)
  (run! print-markdown steps)
  (println))


(defmethod print-markdown :gherkin/scenario [{:keys [name steps]}]
  (println "##" name)
  (println)
  (run! print-markdown steps)
  (println))

(derive :gherkin/scenario-outline :gherkin/scenario)

(defmethod print-markdown :gherkin/step [{:keys [keyword argument text]}]
  (println "-" (poor-hiccup [:em keyword]) text)
  (when argument
    (println)
    (print-markdown argument))
  (println))

(defmethod print-markdown :gherkin/doc-string [{:keys [content content-type]}]
  (println "```" content-type)
  (println content)
  (println "```")
  (println))

(defmethod print-markdown :gherkin/data-table [{:keys [rows]}]
  (println "|" (str/join " | " (first rows)) "|")
  (println (str "|" (apply str (repeat (count (first rows)) "---|"))))
  (doseq [row (rest rows)]
    (println "|" (str/join " | " row) "|"))
  (println))

(comment
  :gherkin/comment
  :gherkin/examples
  :gherkin/tag

  (doseq [f (->> "/home/arne/github/lambdaisland/kaocha/test/features"
                 io/file
                 file-seq
                 (map str)
                 (filter #(str/ends-with? % ".feature")))]
    (with-open [out (-> f
                        (str/replace "test/features" "doc")
                        (str/replace ".feature" ".md")
                        io/writer)]
      (println f)
      (binding [*out* out]
        (->> f
             jvm/parse
             gherkin/gherkin->edn
             print-markdown))))


  (->> "resources/lambdaisland/gherkin/test_feature.feature"
       jvm/parse
       gherkin/gherkin->edn)


  (spit "resources/lambdaisland/gherkin/test_feature.md"
        (with-out-str
          (->> "resources/lambdaisland/gherkin/test_feature.feature"
               jvm/parse
               gherkin/gherkin->edn
               print-markdown))))

