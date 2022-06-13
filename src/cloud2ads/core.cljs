(ns cloud2ads.core
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [reagent.dom :as d]
   [goog.functions]))

;; State
(defonce files (r/atom '()))
(defonce selected-files (r/atom '()))
(defonce facebook-ad-accounts (r/atom {}))
(defonce selected-facebook-ad-account (r/atom ""))

;; Google OAuth
(def google-login-url
  "https://accounts.google.com/o/oauth2/v2/auth?scope=https%3A//www.googleapis.com/auth/drive.readonly&include_granted_scopes=true&response_type=token&state=google_success&redirect_uri=https%3A//localhost:3001&client_id=127351764865-s0s6ivmt8aec5omp6rk84piuamuk1vkv.apps.googleusercontent.com")

;; Handling OAuth redirects
(def url-hash (. js/window -location.hash))
(def url-hash-map
  (into {}
        (map
         (fn [[k v]] [(keyword k) v])
         (map
          #(str/split % #"=")
          (str/split (str/join "" (drop 1 url-hash)) #"&")))))
(when
 (= (get url-hash-map :state) "google_success")
  (js/localStorage.setItem "google_access_token" (get url-hash-map :access_token)) ; store access token
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
  [:div.overflow-auto {:class "max-h-[42rem]"}
   (if (str/blank? @selected-folder)
     [:div "No folder selected"]
     [:div [:a.hover:underline.cursor-pointer {:on-click #(do (reset! selected-folder nil) (reset! files '()) (reset! selected-files '()) (init-get-files))} "↩️ Go back"] [:div "Folder " [:span.font-bold (get @selected-folder "name")] " selected"]])
   (if (empty? @files)
     [:div "No files found"]
     [:div
      (def all-selected? (= (sort-by #(get % "id") @selected-files) (sort-by #(get % "id") @files)))
      [:input.mr-2 {:id "select-all"
                    :type "checkbox"
                    :on-change #(if-not all-selected? (reset! selected-files @files) (reset! selected-files '()))
                    :checked all-selected?}]
      [:label {:for "select-all"} (if-not all-selected? "Select all" "Unselect all")]
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
       [:h1.text-xl.font-bold "Google Drive files"]
       [:div (search-input search-query selected-folder)]
       [:div (file-list search-query selected-folder)]])))

;; Facebook auth
(def facebook-login-url
  (str/join ""
            '("https://www.facebook.com/v14.0/dialog/oauth?"
              "client_id=" "327707909531390"
              "&redirect_uri=" "https%3A//localhost:3001"
              "&response_type=" "token"
              "&scope=" "ads_management,business_management"
              "&state=" "facebook_success")))

(when
 (= (get url-hash-map :state) "facebook_success")
  (js/localStorage.setItem "facebook_access_token" (get url-hash-map :access_token)) ; store access token
  (js/window.history.replaceState {} (. js/document -title) "/")) ; clean URL

;; Facebook login
(defn facebook-login []
  [:div [:a {:href facebook-login-url} "Log in via Facebook"]])

(def facebook-api-base-url "https://graph.facebook.com/v14.0")

(defn facebook-api [endpoint]
  (str facebook-api-base-url endpoint (if (str/includes? endpoint "?") "&" "?") "access_token=" (js/localStorage.getItem "facebook_access_token")))

(defn act_valid? [act] (= (get act "account_status") 1))

;; Listing accounts
(defn init-facebook-list-accounts []
  (->
   (.fetch js/window (facebook-api (str "/me?fields=" (js/encodeURIComponent "name,personal_ad_accounts{name,account_status},businesses{name,client_ad_accounts{name,business_name,account_status},owned_ad_accounts{name,business_name,account_status}}"))))
   (.then #(.json %))
   (.then #(js->clj %))
   (.then #(hash-map "personal"
                     (filter act_valid? (get-in % ["personal_ad_accounts" "data"]))
                     "businesses"
                     (map
                      (fn [business]
                        {"name" (get business "name")
                         "id" (get business "id")
                         "ad_accounts" (concat
                                        (filter act_valid? (get-in business ["client_ad_accounts" "data"] '()))
                                        (filter act_valid? (get-in business ["owned_ad_accounts" "data"] '())))})
                      (get-in % ["businesses" "data"]))))
   (.then #(reset! facebook-ad-accounts %))))

(defn facebook-account-radio [business_id account] [:div.ml-2
                                                    [:input.mr-2 {:id (str business_id ":" (get account "id"))
                                                                  :type "radio"
                                                                  :name "facebook_act_id"
                                                                  :on-change #(reset! selected-facebook-ad-account (-> % .-target .-value))
                                                                  ;; :checked (= (get account "id") @selected-facebook-ad-account)
                                                                  :value (str business_id ":" (get account "id"))}]
                                                    [:label {:for (str business_id ":" (get account "id"))} (get account "name")]])

(defn facebook-account-selector []
  [:div
   [:h1.text-xl.font-bold "Facebook Accounts"]
   [:div "Select your account"]
   (when-not (empty? (get @facebook-ad-accounts "personal")) [:div.font-bold "Personal accounts"])
   (map (fn [personal_account] (facebook-account-radio "personal" personal_account))
        (get @facebook-ad-accounts "personal")) ; personal accounts
   (map (fn [business]
          [:div [:div.font-bold (get business "name")]
           (when (empty? (get business "ad_accounts")) [:div.italic.ml-2 "No ad account in this business"])
           (map (fn [ad_account] (facebook-account-radio (get business "id") ad_account))
                (get business "ad_accounts"))])
        (get @facebook-ad-accounts "businesses"))]) ; businesses

(defn get-facebook-ad-account-from-id [id]
  (let [[business_id account_id] (str/split id #":")]
    (case business_id
      "personal" (->> (get @facebook-ad-accounts "personal") (filter #(= account_id (get % "id"))) (first))
      (let [business (->> (get @facebook-ad-accounts "businesses") (filter #(= business_id (get % "id"))) (first))]
        (->> (get business "ad_accounts") (filter #(= account_id (get % "id"))) (first))))))

(defn gdrive-download-file [file-id] (str "https://www.googleapis.com/drive/v3/files/" file-id "?alt=media"))

(defn generate-form-data [params filename]
  (let [form-data (js/FormData.)]
    (doseq [[k v] params]
      (.append form-data (name k) v filename))
    form-data))

(defn process-file [file]
  (->
   (.fetch js/window (gdrive-download-file (file "id")) gdrive-headers)
   (.then #(.blob %))
   (.then
    (fn [blob]
      (->
       (.fetch js/window
               (facebook-api (str "/" (last (str/split @selected-facebook-ad-account #":")) "/adimages"))
               #js {"method" "post"
                    "body" (generate-form-data {"filename" blob} (file "name"))}))))))

(defn upload-button []
  [:div.text-center
   [:button.rounded-md.text-white.bg-indigo-500.mt-2.p-2
    {:on-click #(map (fn [file] (process-file file)) @selected-files)}
    "DO IT"]])

;; Template
(defn home-page []
  [:div.m-5
   [:div [:h2.text-2xl.text-center.font-bold "Welcome to cloud2ads"]]
   [:div.flex.flex-col.md:flex-row.justify-evenly
    [:div (if (nil? (js/localStorage.getItem "google_access_token")) [google-login] [:div [:div "Logged in via Google 🔗"] [file-picker]])]
    [:div (if (nil? (js/localStorage.getItem "facebook_access_token")) [facebook-login] [:div [:div "Logged in via Facebook 🔗"] [facebook-account-selector]])]]
   [:div#about.text-center.mt-12
    [:span "You will upload "]
    [:span.font-bold (str/join ", " (map #(get % "name") @selected-files))]
    [:span " to Facebook ad account "]
    [:span.font-bold (get (get-facebook-ad-account-from-id @selected-facebook-ad-account) "name")]
    [:span "'s media library"]]
   [upload-button]
   [:div#about.text-center.mt-48 "Created by D."]])

;; Initialize app
(defonce init
  (do
    (when-not (nil? (js/localStorage.getItem "google_access_token")) (init-get-files))
    (when-not (nil? (js/localStorage.getItem "facebook_access_token")) (init-facebook-list-accounts))))

;; Mount app
(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! [] (mount-root))
