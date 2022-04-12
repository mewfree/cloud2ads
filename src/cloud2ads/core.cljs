(ns cloud2ads.core
  (:require
     [clojure.string :as str]
     [reagent.core :as r]
     [reagent.dom :as d]))

;; Google OAuth
(def google-login-url "https://accounts.google.com/o/oauth2/v2/auth?scope=https%3A//www.googleapis.com/auth/drive.readonly&include_granted_scopes=true&response_type=token&state=google_success&redirect_uri=http%3A//localhost:3000&client_id=127351764865-s0s6ivmt8aec5omp6rk84piuamuk1vkv.apps.googleusercontent.com")

;; Handling OAuth redirects
(def url-hash (. js/window -location.hash))
(def url-params
  (into {}
    (map
      (fn [[k v]] [(keyword k) v])
      (map
        #(str/split % #"=")
        (str/split (str/join "" (drop 1 url-hash)) #"&")))))
(when
  (= (get url-params :state) "google_success")
  (js/localStorage.setItem "google_access_token" (get url-params :access_token)) ;; store access token
  (js/window.history.replaceState {} (. js/document -title) "/")) ;; clean URL

;; Google Login
(defn google-login [] [:div [:a {:href google-login-url} "Log in with Google"]])

;; Google Drive API
(def gdrive-url "https://www.googleapis.com/drive/v3/files")
(def gdrive-headers
  #js {"headers"
    #js {"Accept" "application/json"
         "Authorization" (str "Bearer " (js/localStorage.getItem "google_access_token"))}})

;; Google Drive file picker
(def file-list (r/atom '()))
(->
  (.fetch js/window gdrive-url gdrive-headers)
  (.then #(.json %))
  (.then #(js->clj %))
  (.then #(get % "files"))
  (.then #(reset! file-list %))
  ;; (.then #(clj->js %))
  ;; (.then #(js/console.log %))
)
(defn file-picker [] [:div (map (fn [file] [:div (get file "name")]) @file-list)])

;; Template
(defn home-page []
  [:div.m-5
    [:div [:h2.text-2xl.text-center.font-bold "Welcome to cloud2ads"]]
    (if (nil? (js/localStorage.getItem "google_access_token")) [google-login] [:div [:div "Logged in via Google!!!"] [file-picker]])
    [:div "Log in via Facebook"]
    [:div#about.text-center.mt-48 "Created by D."]
   ])

;; Initialize app
(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! []
  (mount-root))
