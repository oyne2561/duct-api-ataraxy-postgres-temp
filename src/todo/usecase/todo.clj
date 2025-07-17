(ns todo.usecase.todo
  (:require [todo.domain.todo :as domain]
            [todo.port.todo-repository :as port]
            [integrant.core :as ig]))

;; Todo ユースケースレコード
(defrecord TodoUsecase [repository])

;; ユースケース実装関数
(defn get-all-todos [usecase]
  {:success true
   :data (port/find-all (:repository usecase))})

(defn get-todo-by-id [usecase id]
  (if-let [todo (port/find-by-id (:repository usecase) id)]
    {:success true
     :data todo}
    {:success false
     :error "Todo not found"
     :error-code :not-found}))

(defn get-todos-by-status [usecase completed]
  {:success true
   :data (port/find-by-status (:repository usecase) completed)})

(defn get-todo-stats [usecase]
  (let [repository (:repository usecase)]
    {:success true
     :data {:total (port/count-todos repository)
            :completed (port/count-completed repository)
            :pending (- (port/count-todos repository)
                        (port/count-completed repository))}}))

(defn create-todo [usecase title description]
  (try
    (let [new-todo (domain/create-todo title description)]
      (if (domain/valid-todo? new-todo)
        (let [saved-todo (port/save (:repository usecase) new-todo)]
          {:success true
           :data saved-todo})
        {:success false
         :error "Invalid todo data"
         :error-code :validation-error}))
    (catch Exception e
      {:success false
       :error (str "Failed to create todo: " (.getMessage e))
       :error-code :internal-error})))

(defn update-todo [usecase id updates]
  (try
    (if-let [existing-todo (port/find-by-id (:repository usecase) id)]
      (let [updated-todo (merge existing-todo 
                                (select-keys updates [:title :description])
                                {:updated-at (java.time.Instant/now)})]
        (if (domain/valid-todo? updated-todo)
          (let [saved-todo (port/save (:repository usecase) updated-todo)]
            {:success true
             :data saved-todo})
          {:success false
           :error "Invalid todo data"
           :error-code :validation-error}))
      {:success false
       :error "Todo not found"
       :error-code :not-found})
    (catch Exception e
      {:success false
       :error (str "Failed to update todo: " (.getMessage e))
       :error-code :internal-error})))

(defn toggle-todo-completion [usecase id]
  (try
    (if-let [existing-todo (port/find-by-id (:repository usecase) id)]
      (let [updated-todo (if (:completed existing-todo)
                           (domain/uncomplete-todo existing-todo)
                           (domain/complete-todo existing-todo))
            saved-todo (port/save (:repository usecase) updated-todo)]
        {:success true
         :data saved-todo})
      {:success false
       :error "Todo not found"
       :error-code :not-found})
    (catch Exception e
      {:success false
       :error (str "Failed to toggle todo: " (.getMessage e))
       :error-code :internal-error})))

(defn delete-todo [usecase id]
  (try
    (if-let [deleted-todo (port/delete (:repository usecase) id)]
      {:success true
       :data deleted-todo}
      {:success false
       :error "Todo not found"
       :error-code :not-found})
    (catch Exception e
      {:success false
       :error (str "Failed to delete todo: " (.getMessage e))
       :error-code :internal-error})))



;; Integrant コンポーネント
(defmethod ig/init-key :todo.usecase/todo [_ {:keys [repository]}]
  (->TodoUsecase repository)) 