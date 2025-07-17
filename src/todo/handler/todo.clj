(ns todo.handler.todo
  (:require [ataraxy.response :as response]
            [todo.usecase.todo :as usecase]
            [integrant.core :as ig]
            [clojure.data.json :as json]))

;; HTTPレスポンスヘルパー
(defn success-response
  ([data] (success-response data 200))
  ([data status]
   [::response/ok data {:status status
                        :headers {"Content-Type" "application/json"}}]))

(defn error-response
  ([message] (error-response message 400))
  ([message status]
   [::response/ok {:error message}
    {:status status
     :headers {"Content-Type" "application/json"}}]))

;; リクエストボディのパース
(defn parse-json-body [request]
  (try
    (when-let [body (:body request)]
      (json/read-str (slurp body) :key-fn keyword))
    (catch Exception e
      nil)))

;; IDの検証とパース
(defn parse-id [id-str]
  (try
    (Integer/parseInt id-str)
    (catch NumberFormatException e
      nil)))

;; Todo HTTPハンドラー
(defn get-todos-handler
  "すべてのTodoを取得するハンドラー"
  [request todo-usecase]
  (let [result (usecase/get-all-todos todo-usecase)]
    (if (:success result)
      (success-response (:data result))
      (error-response (:error result) 500))))

(defn get-todo-handler
  "IDでTodoを取得するハンドラー"
  [request todo-usecase]
  (let [[_ id-str] (:ataraxy/result request)
        id (parse-id id-str)]
    (if id
      (let [result (usecase/get-todo-by-id todo-usecase id)]
        (if (:success result)
          (success-response (:data result))
          (case (:error-code result)
            :not-found (error-response (:error result) 404)
            (error-response (:error result) 500))))
      (error-response "Invalid ID format" 400))))

(defn get-todos-by-status-handler
  "完了状態でTodoをフィルタリングするハンドラー"
  [request todo-usecase]
  (let [params (:query-params request)
        completed-str (get params "completed")
        completed (case completed-str
                    "true" true
                    "false" false
                    nil)]
    (if (boolean? completed)
      (let [result (usecase/get-todos-by-status todo-usecase completed)]
        (if (:success result)
          (success-response (:data result))
          (error-response (:error result) 500)))
      (error-response "Invalid completed parameter. Use 'true' or 'false'" 400))))

(defn get-todo-stats-handler
  "Todo統計を取得するハンドラー"
  [request todo-usecase]
  (let [result (usecase/get-todo-stats todo-usecase)]
    (if (:success result)
      (success-response (:data result))
      (error-response (:error result) 500))))

(defn create-todo-handler
  "新しいTodoを作成するハンドラー"
  [request todo-usecase]
  (if-let [body (parse-json-body request)]
    (let [{:keys [title description]} body]
      (if title
        (let [result (usecase/create-todo todo-usecase title description)]
          (if (:success result)
            (success-response (:data result) 201)
            (case (:error-code result)
              :validation-error (error-response (:error result) 400)
              (error-response (:error result) 500))))
        (error-response "Title is required" 400)))
    (error-response "Invalid JSON body" 400)))

(defn update-todo-handler
  "Todoを更新するハンドラー"
  [request todo-usecase]
  (let [[_ id-str] (:ataraxy/result request)
        id (parse-id id-str)]
    (if id
      (if-let [body (parse-json-body request)]
        (let [updates (select-keys body [:title :description])]
          (if (seq updates)
            (let [result (usecase/update-todo todo-usecase id updates)]
              (if (:success result)
                (success-response (:data result))
                (case (:error-code result)
                  :not-found (error-response (:error result) 404)
                  :validation-error (error-response (:error result) 400)
                  (error-response (:error result) 500))))
            (error-response "No valid fields to update" 400)))
        (error-response "Invalid JSON body" 400))
      (error-response "Invalid ID format" 400))))

(defn toggle-todo-completion-handler
  "Todoの完了状態を切り替えるハンドラー"
  [request todo-usecase]
  (let [[_ id-str] (:ataraxy/result request)
        id (parse-id id-str)]
    (if id
      (let [result (usecase/toggle-todo-completion todo-usecase id)]
        (if (:success result)
          (success-response (:data result))
          (case (:error-code result)
            :not-found (error-response (:error result) 404)
            (error-response (:error result) 500))))
      (error-response "Invalid ID format" 400))))

(defn delete-todo-handler
  "Todoを削除するハンドラー"
  [request todo-usecase]
  (let [[_ id-str] (:ataraxy/result request)
        id (parse-id id-str)]
    (if id
      (let [result (usecase/delete-todo todo-usecase id)]
        (if (:success result)
          (success-response (:data result))
          (case (:error-code result)
            :not-found (error-response (:error result) 404)
            (error-response (:error result) 500))))
      (error-response "Invalid ID format" 400))))

;; Integrant コンポーネント
(defmethod ig/init-key :todo.handler/get-todos [_ {:keys [todo-usecase]}]
  (fn [request] (get-todos-handler request todo-usecase)))

(defmethod ig/init-key :todo.handler/get-todo [_ {:keys [todo-usecase]}]
  (fn [request] (get-todo-handler request todo-usecase)))

(defmethod ig/init-key :todo.handler/get-todos-by-status [_ {:keys [todo-usecase]}]
  (fn [request] (get-todos-by-status-handler request todo-usecase)))

(defmethod ig/init-key :todo.handler/get-todo-stats [_ {:keys [todo-usecase]}]
  (fn [request] (get-todo-stats-handler request todo-usecase)))

(defmethod ig/init-key :todo.handler/create-todo [_ {:keys [todo-usecase]}]
  (fn [request] (create-todo-handler request todo-usecase)))

(defmethod ig/init-key :todo.handler/update-todo [_ {:keys [todo-usecase]}]
  (fn [request] (update-todo-handler request todo-usecase)))

(defmethod ig/init-key :todo.handler/toggle-todo-completion [_ {:keys [todo-usecase]}]
  (fn [request] (toggle-todo-completion-handler request todo-usecase)))

(defmethod ig/init-key :todo.handler/delete-todo [_ {:keys [todo-usecase]}]
  (fn [request] (delete-todo-handler request todo-usecase)))