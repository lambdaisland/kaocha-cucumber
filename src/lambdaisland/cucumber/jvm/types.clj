(ns lambdaisland.cucumber.jvm.types
  (:import [java.io File InputStream FileInputStream]
           [java.util Iterator]
           [java.lang Iterable]
           [cucumber.runtime.io Resource ResourceLoader])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn file-resource ^Resource [^File root ^File file]
  (reify
    Resource
    (getPath ^String [this]
      (.getPath file))
    (getInputStream ^InputStream [this]
      (FileInputStream. file))

    Object
    (toString [this]
      (str "<file-resource: @root=" root ", @file=" file ">"))))

(defn file-resource-seq ^Iterator [root suffix]
  (->> (io/file root)
       file-seq
       (filter (fn [f]
                 (and (.isFile f) (str/ends-with? (str f) suffix))))
       (map #(file-resource root %))))

(defn file-resource-loader ^ResourceLoader []
  (reify ResourceLoader
    (^Iterable resources [this ^String path ^String suffix]
     (file-resource-seq path suffix))))
