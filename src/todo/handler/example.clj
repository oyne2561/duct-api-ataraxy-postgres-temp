(ns todo.handler.example
  (:require [ataraxy.response :as response] 
            [integrant.core :as ig]))

;; ハンドラー関数を定義
(defn example-handler
  [request]
  [::response/ok {:message "Hello from Todo API!"
                  :timestamp (java.time.Instant/now)}])

;; Integrantコンポーネントとして登録
(defmethod ig/init-key :todo.handler/example [_ options]
  example-handler)