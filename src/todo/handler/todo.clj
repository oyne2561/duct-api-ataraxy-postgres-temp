(ns todo.handler.todo
   (:require [ataraxy.response :as response]
            [integrant.core :as ig]))

;; 既存のハンドラー
(defn get-todos [request]
  ;; (println request)
  ;; (println "get-todos")
  [::response/ok {:message "get-todos"}])

;; 新しいハンドラー関数

(defn get-todos-stats [request]
  [::response/ok {:message "get-todos-stats"
                  :total-todos 42
                  :completed 30
                  :pending 12}])

(defn get-todo [request]
  (let [[_ id] (:ataraxy/result request)]
    [::response/ok {:message (str "get-todo with id: " id)
                    :id id
                    :title "Sample Todo"
                    :completed false}]))

(defn create-todo [request]
  [::response/ok {:message "create-todo"
                  :id 123
                  :status "created"}])

(defn update-todo [request]
  (let [[_ id] (:ataraxy/result request)]
    [::response/ok {:message (str "update-todo with id: " id)
                    :id id
                    :status "updated"}]))

(defn delete-todo [request]
  (let [[_ id] (:ataraxy/result request)]
    [::response/ok {:message (str "delete-todo with id: " id)
                    :id id
                    :status "deleted"}]))

;; Integrant コンポーネント登録
(defmethod ig/init-key :todo.handler/get-todos [_ options]
  get-todos)

(defmethod ig/init-key :todo.handler/get-todos-stats [_ options]
  get-todos-stats)

(defmethod ig/init-key :todo.handler/get-todo [_ options]
  get-todo)

(defmethod ig/init-key :todo.handler/create-todo [_ options]
  create-todo)

(defmethod ig/init-key :todo.handler/update-todo [_ options]
  update-todo)

(defmethod ig/init-key :todo.handler/delete-todo [_ options]
  delete-todo)