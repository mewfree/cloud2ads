(ns cloud2ads.core
    (:require
      [clojure.string :as str]
      [reagent.core :as r]
      [reagent.dom :as d]))

;; Views

;; Google OAuth
(def google-login-url "https://accounts.google.com/o/oauth2/v2/auth?scope=https%3A//www.googleapis.com/auth/drive.readonly&include_granted_scopes=true&response_type=token&state=google_success&redirect_uri=http%3A//localhost:3000&client_id=127351764865-s0s6ivmt8aec5omp6rk84piuamuk1vkv.apps.googleusercontent.com")

;; Handling OAuth redirects
(println "coucou")
(def url-hash (. js/window -location.hash))
(def url-params
  (into {}
    (map
     (fn [[k v]] [(keyword k) v])
     (map
      #(str/split % #"=")
      (str/split (str/join "" (drop 1 url-hash)) #"&")))))
(println url-params)
(println (get url-params :state))
(if
  (= (get url-params :state) "google_success")
  (do
    (js/localStorage.setItem "google_access_token" (get url-params :access_token)) ;; store access token
    (js/window.history.replaceState {} (. js/document -title) "/")) ;; clean URL
  (println "not a google success"))

;; Template
(defn home-page []
  [:div.m-5
    [:div [:h2.text-2xl.text-center.font-bold "Welcome to cloud2ads"]]
    [:div [:a {:href google-login-url} "Log in with Google"]]
    [:div "Log in via Facebook"]
    [:div#about.text-center.mt-48 "Created by D."]
   ])

;; Initialize app

(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! []
  (mount-root))
