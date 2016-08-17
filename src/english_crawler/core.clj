(ns english-crawler.core
  (:require [feedparser-clj.core :as feedparser])
  (:require [taika.core :as taika])
  (:require [taika.auth :as taika-auth])
  (use clojure.pprint)
  (:gen-class))

(def p pprint); for debug
(def firebase-token "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
(def firebase-db-name "XXXXXXXXXXX-XXXXX")

(def src '(
  { :name "The Japan Times", :feed-url "http://www.japantimes.co.jp/feed/topstories/" }
  { :name "Japan Today", :feed-url "http://www.japantoday.com/feed" }))

(defn one-src-to-entries [one-src]
  (def entries (:entries (feedparser/parse-feed (:feed-url one-src))))
  (def simple-entries(map #(select-keys % [:title :link :site-name]) entries))
  (map #(assoc % :site-name (:name one-src)) simple-entries))

(def user-auth-token
  (let [token-generator (taika-auth/token-generator firebase-token)
        auth-data {:username "taika" :team_id 100}
        admin? false]
    (taika-auth/create-token token-generator auth-data admin?)))

(defn -main [& args]
  (def write-data (flatten (map one-src-to-entries src)))
  (doseq [data write-data]
    (taika/update! firebase-db-name "/news" { (hash (:link data)) data } user-auth-token)))
