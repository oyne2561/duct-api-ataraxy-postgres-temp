(ns todo.gateway.todo-repository
  (:require [todo.port.todo-repository :as port]
            [todo.domain.todo :as domain]
            [integrant.core :as ig]))

;; インメモリーTodoリポジトリの実装
(defrecord InMemoryTodoRepository [data-store next-id]
  port/TodoRepository
  
  (find-all [_]
    (vals @data-store))
  
  (find-by-id [_ id]
    (get @data-store id))
  
  (find-by-status [_ completed]
    (->> @data-store
         vals
         (filter #(= (:completed %) completed))))
  
  (save [this todo]
    (let [id (or (:id todo) (swap! next-id inc))
          todo-with-id (assoc todo :id id)]
      (swap! data-store assoc id todo-with-id)
      todo-with-id))
  
  (delete [_ id]
    (let [deleted (get @data-store id)]
      (swap! data-store dissoc id)
      deleted))
  
  (count-todos [_]
    (count @data-store))
  
  (count-completed [_]
    (->> @data-store
         vals
         (filter :completed)
         count)))

;; ファクトリー関数
(defn create-in-memory-repository
  "サンプルデータを含むインメモリーリポジトリを作成する"
  []
  (let [sample-todos
        {1 {:id 1
            :title "Clojureを学習する"
            :description "関数型プログラミングの基本を理解する"
            :completed false
            :created-at (java.time.Instant/parse "2024-01-01T10:00:00Z")
            :updated-at (java.time.Instant/parse "2024-01-01T10:00:00Z")}
         2 {:id 2
            :title "TODOアプリを作成する"
            :description "クリーンアーキテクチャで実装する"
            :completed false
            :created-at (java.time.Instant/parse "2024-01-02T09:00:00Z")
            :updated-at (java.time.Instant/parse "2024-01-02T09:00:00Z")}
         3 {:id 3
            :title "ドキュメントを読む"
            :description "Ductフレームワークのドキュメントを確認"
            :completed true
            :created-at (java.time.Instant/parse "2024-01-01T08:00:00Z")
            :updated-at (java.time.Instant/parse "2024-01-01T15:00:00Z")}}
        data-store (atom sample-todos)
        next-id (atom 3)]
    (->InMemoryTodoRepository data-store next-id)))

;; Integrant コンポーネント
(defmethod ig/init-key :todo.gateway/todo-repository [_ options]
  (create-in-memory-repository)) 