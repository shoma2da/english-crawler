; Prepare
(ns english-crawler.core
  (:require [feedparser-clj.core :as feedparser])
  (:require [taika.core :as taika])
  (:require [taika.auth :as taika-auth])
  (:require [clj-slack.chat :as slack])
  (:require [clj-time.format :as formatter])
  (use clojure.pprint)
  (use pl.danieljanus.tagsoup)
  (:gen-class))
(def p pprint); for debug

(def built-in-formatter )

; Feed data
(def src (list
  { :name "The Japan Times",
    :feed-url "http://www.japantimes.co.jp/feed/topstories/",
    :get-published-date (fn [entry]
                          (time (Thread/sleep 3000))
                          (def link (:link entry))
                          (def maps (filter #(instance? clojure.lang.PersistentArrayMap %) (flatten (parse link))))
                          (def datetime (:datetime (first (filter #(contains? % :datetime) maps))))
                          (.toDate (formatter/parse (formatter/formatters :date-time-no-ms) datetime))) }
  { :name "Japan Today",
    :feed-url "http://www.japantoday.com/feed",
    :get-published-date (fn [entry] (:published-date entry)) }))


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
(defn append-id [entry]
  (def id (hash (:link entry)))
  (merge entry {:id (str id)} ))

(defn entries-from-src [one-src]
  (def entries (:entries (feedparser/parse-feed (:feed-url one-src))))
  (map #(hash-map :entry (append-id %), :src one-src) entries))

(defn is-already-saved [entry]
  (def id (:id (:entry entry)))
  (some #(= id %) (keys saved-entries)))

(defn append-datetime [{entry :entry, src :src}]
  (def datetime ((:get-published-date src) entry))
  (list :entry (merge entry { :datetime datetime }), :src src))

(defn to-firebase-content [{entry :entry, src :src}]
  {
    (:id entry)
    {
       :site-name (:name src)
       :link (:link entry)
       :title (:title entry)
       :datetime (:datetime entry)
     }
   })

(defn insert-news-to-firebase [data]
  (taika/update! firebase-db-name "/news" data firebase-user-auth-token))

(defn new-data-message [new-data]
  (if (not= 0 (count new-data))
    (str "【クロール結果】" (count new-data) " 件の新着ニュースをFirebaseに保存しました")))

(defn post-message-to-slack [message]
  (slack/post-message slack-connection slack-room-name message))


; Main
(defn -main [& args]
  (def entries (flatten (map entries-from-src src)))
  (def new-entries (remove is-already-saved entries))
  (def additional-new-entries (map append-datetime new-entries))
  (doseq [data (map to-firebase-content additional-new-entries)]
    (insert-news-to-firebase data)
    (p (:title (first (vals data)))))
  (post-message-to-slack (new-data-message new-entries)))
