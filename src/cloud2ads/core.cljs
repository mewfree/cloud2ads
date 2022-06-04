(ns cloud2ads.core
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [reagent.dom :as d]
   [goog.functions]))

;; State
(defonce files (r/atom '()))
(defonce selected-files (r/atom '()))

;; Google OAuth
(def google-login-url
  "https://accounts.google.com/o/oauth2/v2/auth?scope=https%3A//www.googleapis.com/auth/drive.readonly&include_granted_scopes=true&response_type=token&state=google_success&redirect_uri=http%3A//localhost:3000&client_id=127351764865-s0s6ivmt8aec5omp6rk84piuamuk1vkv.apps.googleusercontent.com")

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
  (js/localStorage.setItem "google_access_token" (get url-params :access_token)) ; store access token
  (js/window.history.replaceState {} (. js/document -title) "/")) ; clean URL

;; Google Login
(defn google-login []
  [:div [:a {:href google-login-url} "Log in with Google"]])

;; Google Drive API
(defn gdrive-url-init [] (str "https://www.googleapis.com/drive/v3/files?q=%28mimeType+contains+%27image%27+or+mimeType+%3D+%27application%2Fvnd.google-apps.folder%27%29"))
(defn gdrive-url-query [query] (str "https://www.googleapis.com/drive/v3/files?q=%28mimeType+contains+%27image%27+or+mimeType+%3D+%27application%2Fvnd.google-apps.folder%27%29+and+name+contains+%27" query "%27"))
(defn gdrive-url-folder [folder-id] (str "https://www.googleapis.com/drive/v3/files?q=%28mimeType+contains+%27image%27+or+mimeType+%3D+%27application%2Fvnd.google-apps.folder%27%29+and+%27" folder-id "%27 in parents"))
(def gdrive-headers
  #js {"headers"
       #js {"Accept" "application/json"
            "Authorization" (str "Bearer " (js/localStorage.getItem "google_access_token"))}})

;; Google Drive file picker
(defn init-get-files []
  (->
   (.fetch js/window (gdrive-url-init) gdrive-headers)
   (.then #(.json %))
   (.then #(js->clj %))
   (.then #(if (= (get-in % ["error" "code"]) 401) (set! (.-location js/window) google-login-url) %)) ; if token expired, redirect to Google's prompt
   (.then #(get % "files"))
   (.then #(reset! files %))))

(defn update-files-query [query]
  (->
   (.fetch js/window (gdrive-url-query @query) gdrive-headers)
   (.then #(.json %))
   (.then #(js->clj %))
   (.then #(if (= (get-in % ["error" "code"]) 401) (set! (.-location js/window) google-login-url) %)) ; if token expired, redirect to Google's prompt
   (.then #(get % "files"))
   (.then #(reset! files %))))

(defn update-files-folder [folder-id]
  (->
   (.fetch js/window (gdrive-url-folder folder-id) gdrive-headers)
   (.then #(.json %))
   (.then #(js->clj %))
   (.then #(if (= (get-in % ["error" "code"]) 401) (set! (.-location js/window) google-login-url) %)) ; if token expired, redirect to Google's prompt
   (.then #(get % "files"))
   (.then #(reset! files %))))

(def debounced-update-files-query (goog.functions.debounce update-files-query 600))

;; Search bar
(defn search-input [query selected-folder]
  [:input.border.w-full.p-1 {:type "text"
                             :value @query
                             :on-change #(do (reset! query (-> % .-target .-value)) (reset! selected-folder nil) (reset! selected-files '()) (debounced-update-files-query query))}])

;; File list
(defn file-list [query selected-folder]
  [:div
   (if (str/blank? @selected-folder)
     [:div "No folder selected"]
     [:div [:a.hover:underline.cursor-pointer {:on-click #(do (reset! selected-folder nil) (reset! files '()) (reset! selected-files '()) (init-get-files))} "↩️ Go back"] [:div "Folder " [:span.font-bold (get @selected-folder "name")] " selected"]])
   (if (empty? @files)
     [:div "No files found"]
     [:div
      [:input.mr-2 {:id "select-all" :type "checkbox" :checked (= @selected-files sort @files)}] [:label {:for "select-all"} "Select all"]
      (doall
       (map
        (fn [file]
          (def folder? (= (get file "mimeType") "application/vnd.google-apps.folder"))
          [:div {:key (get file "id")}
           [:input.mr-2 {:id (get file "id")
                         :type "checkbox"
                         :disabled folder?
                         :on-change (fn []
                                      (if (some #(= file %) @selected-files)
                                        (reset! selected-files (remove #(= file %) @selected-files))
                                        (reset! selected-files (cons file @selected-files))))
                         :checked (some? (some #(= file %) @selected-files))}]
           (if folder?
             [:a.hover:underline.cursor-pointer {:on-click #(do (reset! query "") (reset! selected-folder file) (reset! files '()) (reset! selected-files '()) (update-files-folder (get @selected-folder "id")))} (str "📂 " (get file "name"))]
             [:label {:for (get file "id")} (str "🖼 " (get file "name"))])])
        @files))])])

;; File picker
(defn file-picker []
  (let [search-query (r/atom "") selected-folder (r/atom nil)]
    (fn []
      [:div
       [:div (search-input search-query selected-folder)]
       [:div (file-list search-query selected-folder)]
       [:div (str "Selected: " (str/join ", " (map #(get % "name") @selected-files)))]]))) ;; convert to thread-last?

;; Template
(defn home-page []
  [:div.m-5
   [:div [:h2.text-2xl.text-center.font-bold "Welcome to cloud2ads"]]
   (if (nil? (js/localStorage.getItem "google_access_token")) [google-login] [:div [:div "Logged in via Google 🔗"] [file-picker]])
   [:div.mt-24 "Log in via Facebook"]
   [:div#about.text-center.mt-48 "Created by D."]])

;; Initialize app
(defonce init
  (do
    (init-get-files)))

;; Mount app
(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! [] (mount-root))
