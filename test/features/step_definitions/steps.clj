(ns my-steps
  (:require [lambdaisland.cucumber.dsl :refer :all]))

(println "in step definitions")

(defn day? [day asked]
  (if (= day asked)
    "Yes"
    "Nope"))

(Given "today is (.*)" [m today]
  (assoc m :today today))

(When "I ask whether it's (.*) yet" [m day]
  (assoc m :day day))

(Then "I should be told {string}" [{:keys [today day]} string]
  (assert (= (day? day asked) string)))
