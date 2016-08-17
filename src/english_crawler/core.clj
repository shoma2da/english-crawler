(ns english-crawler.core
  (:require [feedparser-clj.core :as feedparser])
  (use clojure.zip)
  (use clojure.pprint)
  (:gen-class))

(def p pprint)

(def src '(
  { :name "The Japan Times", :feed-url "http://www.japantimes.co.jp/feed/topstories/" }))

(defn one-src-to-entries [one-src]
  (def entries (:entries (feedparser/parse-feed (:feed-url one-src))))
  (def simple-entries(map #(select-keys % [:title :link :site-name]) entries))
  (map #(assoc % :site-name (:name one-src)) simple-entries))

(defn -main [& args]
  (p (flatten (map one-src-to-entries src))))
