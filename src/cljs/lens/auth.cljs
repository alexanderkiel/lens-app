(ns lens.auth
  (:require [plumbing.core :refer [assoc-when] :refer-macros [fnk]]
            [hodgepodge.core :as storage :refer [session-storage]]
            [om.core :as om]
            [lens.event-bus :as bus]
            [lens.io :as io]
            [schema.core :as s :refer [Str] :include-macros true]))

;; ---- Session Storage -------------------------------------------------------

(s/defn ^:private get-token :- (s/maybe Str) []
  (storage/get-item session-storage "token"))

(s/defn ^:private set-token! [token :- Str]
  (storage/set-item session-storage "token" token))

(defn- remove-token! []
  (storage/remove-item session-storage "token"))

;; ---- Loops -----------------------------------------------------------------

(defn sign-in-loop [owner]
  (bus/listen-on owner :sign-in
    (fnk [username password]
      (io/json-post
        {:url (str (om/get-shared owner :auth-service) "/token")
         :data
         {:grant_type "password"
          :username username
          :password password}
         :on-complete
         (fn [resp]
           (set-token! (resp "access_token"))
           (bus/publish! owner :signed-in {:username username}))}))))

(defn sign-out! [owner]
  (remove-token!)
  (bus/publish! owner :signed-out {}))

(defn sign-out-loop [owner]
  (bus/listen-on owner :sign-out #(sign-out! owner)))

;; ---- Others ----------------------------------------------------------------

(defn assoc-auth-token [opts]
  (if-let [token (get-token)]
    (assoc-in opts [:headers "Authorization"] (str "Bearer " token))
    opts))

(defn validate-token
  "Executed once at page load. Looks up a token in session storage and
  introspects it at the auth service. In case the token is still active, shows
  the current signed in user. Clears the token otherwise."
  [owner]
  (when-let [token (get-token)]
    (io/json-post
      {:url (str (om/get-shared owner :auth-service) "/introspect")
       :data {:token token}
       :on-complete
       (fn [resp]
         (if (resp "active")
           (bus/publish! owner :signed-in {:username (resp "username")})
           (sign-out! owner)))})))
