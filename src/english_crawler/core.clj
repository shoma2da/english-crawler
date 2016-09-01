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


;-----------------------------------
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

(defn insert-news-to-firebase [data]
  (taika/update! firebase-db-name "/news" data firebase-user-auth-token))


;-----------------------------------
; Slack
(def slack-token (System/getenv "SLACK_TOKEN"))
(def slack-room-name(System/getenv "SLACK_ROOM_NAME"))
(def slack-connection {:api-url "https://slack.com/api" :token slack-token})

(defn post-message-to-slack [new-data]
  (def message
    (if (not= 0 (count new-data))
      (str "【クロール結果】" (count new-data) " 件の新着ニュースをFirebaseに保存しました")))
  (if (not (nil? message)) (slack/post-message slack-connection slack-room-name message)))


;-----------------------------------
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

(defn append-additional [{entry :entry, src :src}]
  (def additional ((:get-additional src) entry))
  (list :entry (merge entry additional), :src src))

(defn to-firebase-content [{entry :entry, src :src}]
  {
    (:id entry)
    {
       :site-name (:name src)
       :link (:link entry)
       :title (:title entry)
       :datetime (:datetime entry)
       :image-url (:image-url entry)
       :text (:text entry)
     }
   })


(defn find-text-div [[head & tail] is-target-function]
  (when-not (nil? head)
    (def is-target (is-target-function head))
    (if is-target
      head
      (do
        (def result (if (vector? head) (find-text-div head is-target-function)))
        (if (nil? result)
          (find-text-div tail is-target-function)
          result)))))


(defn element-to-text-list [element]
  (cond
    (nil? element) nil
    (string? element) element
    (vector? element) (let [[key param first-child & children] element]
                        (cond
                          (= key :br) "\n"
                          (= key :script) nil
                          :else (do
                                  (def sentence-list
                                    (flatten
                                      (list
                                        (element-to-text-list first-child)
                                        (map element-to-text-list children))))
                                  (clojure.string/join "" (filter #(not (nil? %)) sentence-list)))))))

(defn text-div-to-text [content]
  (let [[_ _ & children] content]
    (def text-list (filter #(not (empty? %)) (map element-to-text-list children)))
    (clojure.string/join "\n\n" text-list)))


;-----------------------------------
; Feed data
(def src (list
  { :name "Japan Today",
    :feed-url "http://www.japantoday.com/feed",
    :get-additional (fn [entry] ;TODO Runtime Polymorphism をつかう
                      (time (Thread/sleep 3000))
                      (def link (:link entry))
                      (def content (parse link))
                      (def text (text-div-to-text (find-text-div content (fn [head] (and
                                                              (vector? head)
                                                              (= :div (first head)))))))
                      (def date (:published-date entry))
                      { :datetime date, :image-url nil, :text text }) }
  { :name "TechCrunch"
    :feed-url "http://feeds.feedburner.com/TechCrunch/"
    :get-additional (fn [entry]
                      (time (Thread/sleep 3000))
                      (def link (:link entry))
                      (def proxy-content (filter #(instance? clojure.lang.PersistentArrayMap %) (flatten (parse link))))
                      (def real-link (:href (first (filter #(contains? % :href) proxy-content))))
                      (def content (parse real-link))
                      (def text (text-div-to-text (find-text-div content (fn [head]
                                                                           (and
                                                                             (vector? head)
                                                                             (def classValue (:class (second head)))
                                                                             (not (nil? classValue))
                                                                             (.contains classValue "article-entry text")))
                                                                           )))
                      (def maps (filter #(instance? clojure.lang.PersistentArrayMap %) (flatten content)))
                      (def image-url (:content (first (filter #(= (:property %) "og:image") maps))))
                      (def date (:published-date entry))
                      { :datetime date, :image-url image-url, :text text, :link real-link }) }))


;-----------------------------------
; Main
(defn -main [& args]
  (def entries (flatten (map entries-from-src src)))
  (def new-entries (remove is-already-saved entries))
  (def additional-new-entries (map append-additional new-entries))
  (doseq [data (map to-firebase-content additional-new-entries)]
    (insert-news-to-firebase data)
    (p (:title (first (vals data)))))
  (post-message-to-slack new-entries))
