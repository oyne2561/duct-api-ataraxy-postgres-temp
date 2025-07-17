(ns todo.handler.example
  (:require [ataraxy.response :as response] 
            [integrant.core :as ig]))

;; ハンドラー関数を定義
(defn example-handler
  [request deps]
  [::response/ok {:message (:message "デフォルトメッセージ")
                  :timestamp (java.time.Instant/now)
                  :deps deps
                  :request request}])

;; Integrantコンポーネントとして登録
(defmethod ig/init-key :todo.handler/example [_ deps]
  (fn [request]
    (example-handler request deps)))


