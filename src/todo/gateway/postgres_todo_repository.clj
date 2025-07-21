(ns todo.gateway.postgres-todo-repository
  (:require [todo.port.todo-repository :as port]
            [todo.domain.todo :as domain]
            [integrant.core :as ig]
            [duct.database.sql]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]))

;; PostgreSQL TodoRepository実装
(defrecord PostgresTodoRepository [db-spec]
  port/TodoRepository
  
  (find-all [_]
    (jdbc/query db-spec
                ["SELECT id, title, description, completed, created_at, updated_at 
                  FROM todos ORDER BY created_at DESC"]
                {:row-fn (fn [row]
                          {:id (:id row)
                           :title (:title row)
                           :description (:description row)
                           :completed (:completed row)
                           :created-at (.toInstant (:created_at row))
                           :updated-at (.toInstant (:updated_at row))})}))
  
  (find-by-id [_ id]
    (first (jdbc/query db-spec
                       ["SELECT id, title, description, completed, created_at, updated_at 
                         FROM todos WHERE id = ?" id]
                       {:row-fn (fn [row]
                                 {:id (:id row)
                                  :title (:title row)
                                  :description (:description row)
                                  :completed (:completed row)
                                  :created-at (.toInstant (:created_at row))
                                  :updated-at (.toInstant (:updated_at row))})})))
  
  (find-by-status [_ completed]
    (jdbc/query db-spec
                ["SELECT id, title, description, completed, created_at, updated_at 
                  FROM todos WHERE completed = ? ORDER BY created_at DESC" completed]
                {:row-fn (fn [row]
                          {:id (:id row)
                           :title (:title row)
                           :description (:description row)
                           :completed (:completed row)
                           :created-at (.toInstant (:created_at row))
                           :updated-at (.toInstant (:updated_at row))})}))
  
  (save [_ todo]
    (let [now (java.time.Instant/now)]
      (if (:id todo)
        ;; 更新
        (do
          (jdbc/update! db-spec :todos
                        {:title (:title todo)
                         :description (:description todo)
                         :completed (:completed todo)
                         :updated_at (java.sql.Timestamp/from now)}
                        ["id = ?" (:id todo)])
          (assoc todo :updated-at now))
        ;; 新規作成
        (let [result (first (jdbc/insert! db-spec :todos
                                          {:title (:title todo)
                                           :description (:description todo)
                                           :completed (:completed todo false)
                                           :created_at (java.sql.Timestamp/from now)
                                           :updated_at (java.sql.Timestamp/from now)}))]
          {:id (:id result)
           :title (:title todo)
           :description (:description todo)
           :completed (:completed todo false)
           :created-at now
           :updated-at now}))))
  
  (delete [_ id]
    (let [todo (first (jdbc/query db-spec
                                  ["SELECT id, title, description, completed, created_at, updated_at 
                                    FROM todos WHERE id = ?" id]
                                  {:row-fn (fn [row]
                                            {:id (:id row)
                                             :title (:title row)
                                             :description (:description row)
                                             :completed (:completed row)
                                             :created-at (.toInstant (:created_at row))
                                             :updated-at (.toInstant (:updated_at row))})}))]
      (when todo
        (jdbc/delete! db-spec :todos ["id = ?" id])
        todo)))
  
  (count-todos [_]
    (:count (first (jdbc/query db-spec ["SELECT COUNT(*) as count FROM todos"]))))
  
  (count-completed [_]
    (:count (first (jdbc/query db-spec ["SELECT COUNT(*) as count FROM todos WHERE completed = true"])))))

;; ファクトリー関数
(defn create-postgres-repository
  "PostgreSQL用リポジトリを作成する"
  [db-spec]
  (->PostgresTodoRepository db-spec))

;; Integrant コンポーネント
(defmethod ig/init-key :todo.gateway/postgres-todo-repository [_ {:keys [db]}]
  (create-postgres-repository (:spec db))) 