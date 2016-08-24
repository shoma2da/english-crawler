(defproject english-crawler "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [org.clojars.scsibug/feedparser-clj "0.4.0"]
                 [clj-tagsoup "0.3.0"] ;for HTML Parse
                 [com.zenboxapp/taika "0.1.4"] ;for Firebase
                 [org.julienxx/clj-slack "0.5.4"]
                 [clj-time "0.12.0"]
                 ]
  :main ^:skip-aot english-crawler.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
