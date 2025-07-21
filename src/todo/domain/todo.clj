(ns todo.domain.todo
  (:require [clojure.spec.alpha :as s]))

;; :: はClojureのnamespace-qualified keywordを作成するための構文です
;; :: を使うと、現在のnamespaceが自動的にキーワードの前に付与されます。
(s/def ::id pos-int?)  ; これは実際には :todo.domain.todo/id と同じ
(s/def ::title (s/and string? #(> (count %) 0)))
(s/def ::description (s/nilable string?))
(s/def ::completed boolean?)
(s/def ::created-at inst?)
(s/def ::updated-at inst?)

(s/def ::todo
  (s/keys :req-un [::title ::completed ::created-at ::updated-at]
          :opt-un [::id ::description]))

;; Todo エンティティのコンストラクタ
(defn create-todo
  "新しいTodoエンティティを作成する"
  ([title]
   (create-todo title nil))
  ([title description]
   (let [now (java.time.Instant/now)]
     {:id nil  ; IDは保存時に設定される
      :title title
      :description description
      :completed false
      :created-at now
      :updated-at now})))

;; ドメインロジック
(defn complete-todo
  "Todoを完了状態にする"
  [todo]
  (-> todo
      (assoc :completed true)
      (assoc :updated-at (java.time.Instant/now))))

(defn uncomplete-todo
  "Todoを未完了状態にする"
  [todo]
  (-> todo
      (assoc :completed false)
      (assoc :updated-at (java.time.Instant/now))))

(defn update-todo-title
  "Todoのタイトルを更新する"
  [todo new-title]
  (-> todo
      (assoc :title new-title)
      (assoc :updated-at (java.time.Instant/now))))

(defn update-todo-description
  "Todoの説明を更新する"
  [todo new-description]
  (-> todo
      (assoc :description new-description)
      (assoc :updated-at (java.time.Instant/now))))

;; バリデーション
(defn valid-todo?
  "Todoエンティティが有効かチェックする"
  [todo]
  (s/valid? ::todo todo)) 