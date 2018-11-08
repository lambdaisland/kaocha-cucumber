(ns kaocha.cucumber.extra-types)

(defprotocol Color
  (hex [this]))

(defrecord HexColor [r g b]
  Color
  (hex [_] (format "#%02x%02x%02x" r g b)))

(defn parse-color [c]
  (case c
    "red"
    (->HexColor 255 0 0)
    "green"
    (->HexColor 0 255 0)
    "blue"
    (->HexColor 0 0 255)
    (->HexColor 50 50 50)))
