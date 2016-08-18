(ns english-crawler.core
  (:require [feedparser-clj.core :as feedparser])
  (:require [taika.core :as taika])
  (:require [taika.auth :as taika-auth])
  (:require [clj-slack.chat :as slack])
  (use clojure.pprint)
  (:gen-class))

(def p pprint); for debug
(def firebase-token "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
(def firebase-db-name "XXXXXXXXXXX-XXXXX")
(def slack-token "XXXX-XXXXXXXXXX-XXXXXXXXXXXXXXXXXXXXXXXX")
(def slack-room-name "POST_TARGET_ROOM_NAME")

(def src '(
  { :name "The Japan Times", :feed-url "http://www.japantimes.co.jp/feed/topstories/" }
  { :name "Japan Today", :feed-url "http://www.japantoday.com/feed" }))

(def connection {:api-url "https://slack.com/api" :token slack-token})

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
    (taika/update! firebase-db-name "/news" { (hash (:link data)) data } user-auth-token))
    ;TODO すべてupdateしないで、まだ入っていないデータのみにした方が良さそう
  (slack/post-message connection slack-room-name "【英語アプリ】ニュースサイトのクロールが終わりました"))
    ;TODO 取得結果の概要などもポストすると良さそう（○○件追加とか）
