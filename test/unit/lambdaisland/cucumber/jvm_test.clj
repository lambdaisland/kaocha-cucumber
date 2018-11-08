(ns lambdaisland.cucumber.jvm-test
  (:require [clojure.test :refer :all]
            [lambdaisland.cucumber.jvm :as jvm]))

(deftest camel->kepab-test
  (is (= "foo-bar-baz"
         (jvm/camel->kebap "FooBarBaz")))

  (is (= "foo-bar"
         (jvm/camel->kebap "fooBar"))))

(deftest clojure-snippet-test
  (testing "template"
    (is (= "({0} \"{1}\" [{3}]\n  ;; {4}\n{5}  (pending!))\n"
           (.template (jvm/clojure-snippet)))))

  (testing "arguments"
    (is (= "state"
           (.arguments (jvm/clojure-snippet) {})))

    (is (= "state string int"
           (.arguments (jvm/clojure-snippet) (doto (java.util.LinkedHashMap.)
                                               (.put "string" String)
                                               (.put "int" Integer)))))))
