; Prepare
(ns english-crawler.core
  (:require [feedparser-clj.core :as feedparser])
  (:require [taika.core :as taika])
  (:require [taika.auth :as taika-auth])
  (:require [clj-slack.chat :as slack])
  (use clojure.pprint)
  (:gen-class))
(def p pprint); for debug


; Feed data
(def src '(
  { :name "The Japan Times", :feed-url "http://www.japantimes.co.jp/feed/topstories/" }
  { :name "Japan Today", :feed-url "http://www.japantoday.com/feed" }))


; Firebase
(def firebase-token (System/getenv "FIREBASE_TOKEN"))
(def firebase-db-name (System/getenv "FIREBASE_DB_NAME"))
(def firebase-user-auth-token
  (let [token-generator (taika-auth/token-generator firebase-token)
        auth-data {:username "taika" :team_id 100}
        admin? false]
    (taika-auth/create-token token-generator auth-data admin?)))
(def saved-entries
  (taika/read firebase-db-name "/news/"))


; Slack
(def slack-token (System/getenv "SLACK_TOKEN"))
(def slack-room-name(System/getenv "SLACK_ROOM_NAME"))
(def slack-connection {:api-url "https://slack.com/api" :token slack-token})


; Functions
(defn id [data]
  (hash (:link data)))

(defn fetch-from-rss [one-src]
  (def entries (:entries (feedparser/parse-feed (:feed-url one-src))))
  (def simple-entries(map #(select-keys % [:title :link :site-name]) entries))
  (def rss-data (map #(assoc % :site-name (:name one-src)) simple-entries))
  (map (fn [data] { (id data) data }) rss-data))

(defn already-saved [data]
  (def entry-id (str (first (keys data))))
  (contains? saved-entries entry-id))

(defn insert-news-to-firebase [data]
  (taika/update! firebase-db-name "/news" data firebase-user-auth-token))

(defn new-data-message [new-data]
  (if (= 0 (count new-data))
    "【クロール結果】新着ニュースはありませんでした"
    (str "【クロール結果】上記 " (count new-data) " 件の新着ニュースをFirebaseに保存しました")))

(defn post-message-to-slack [message]
  (slack/post-message slack-connection slack-room-name message))


; Main
(defn -main [& args]
  (def rss-data (mapcat fetch-from-rss src))
  (def new-data (remove already-saved rss-data))
  (doseq [data new-data]
    (insert-news-to-firebase data)
    (post-message-to-slack (:title (first (vals data)))))
  (post-message-to-slack (new-data-message new-data)))
