(ns my-steps
  (:require [lambdaisland.cucumber.dsl :refer :all]
            [clojure.test :refer :all]))

(defn day? [day asked]
  (if (= day asked)
    "Yes"
    "Nope"))

(Given "today is (.*)" [m today]
  (assoc m :today today))

(When "I ask whether it's (.*) yet" [m day]
  (assoc m :asked day))

(Then "I should be told {string}" [{:keys [today asked] :as state} string]
  (is (= (day? today asked) string))
  state)


(Then "I should have {int} cucumbers" [state cnt]
  (assoc state :cukes cnt))

(Given "a {color} ball" [state color]
  (prn (.hex color))
  state)

