# 依存関係の逆転（Dependency Inversion）の仕組み

## 概要

このプロジェクトでは、クリーンアーキテクチャの依存関係の逆転原則を実装しています。Port（インターフェース）とGateway（実装）の関係を通じて、ビジネスロジックがインフラストラクチャの詳細に依存しない構造を実現しています。

## アーキテクチャ図

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Handler   │────│   Usecase   │────│    Port     │
│   (HTTP)    │    │(ビジネス    │    │(インター    │
│             │    │ ロジック)   │    │ フェース)   │
└─────────────┘    └─────────────┘    └─────────────┘
                                              ▲
                                              │
                                              │ implements
                                              │
                                       ┌─────────────┐
                                       │   Gateway   │
                                       │   (実装)    │
                                       │             │
                                       └─────────────┘
```

## 依存関係の方向

### 従来のアーキテクチャ（問題あり）
```
Handler → Usecase → Repository実装
```
- UsecaseがRepository実装に直接依存
- 実装を変更するとUsecaseも変更が必要
- テストが困難

### 依存関係の逆転後（改善版）
```
Handler → Usecase → Port ← Gateway
```
- UsecaseはPortのインターフェースにのみ依存
- Gatewayが具体的な実装を提供
- 実装の詳細とビジネスロジックが分離

## 具体的な実装

### 1. Port（インターフェース定義）

```clojure
;; src/todo/port/todo_repository.clj
(ns todo.port.todo-repository)

(defprotocol TodoRepository
  "Todoエンティティのデータアクセスを抽象化するプロトコル"
  
  (find-all [this]
    "すべてのTodoを取得する")
  
  (find-by-id [this id]
    "IDでTodoを取得する")
  
  (save [this todo]
    "Todoを保存する（新規作成または更新）")
  
  (delete [this id]
    "IDでTodoを削除する"))
```

### 2. Gateway（実装）

```clojure
;; src/todo/gateway/todo_repository.clj
(ns todo.gateway.todo-repository
  (:require [todo.port.todo-repository :as port]))

(defrecord InMemoryTodoRepository [data-store next-id]
  port/TodoRepository
  
  (find-all [_]
    (vals @data-store))
  
  (find-by-id [_ id]
    (get @data-store id))
  
  (save [this todo]
    (let [id (or (:id todo) (swap! next-id inc))
          todo-with-id (assoc todo :id id)]
      (swap! data-store assoc id todo-with-id)
      todo-with-id))
  
  (delete [_ id]
    (let [deleted (get @data-store id)]
      (swap! data-store dissoc id)
      deleted)))
```

### 3. Usecase（ビジネスロジック）

```clojure
;; src/todo/usecase/todo.clj
(ns todo.usecase.todo
  (:require [todo.port.todo-repository :as port]))

(defrecord TodoUsecase [repository])

(defn get-all-todos [usecase]
  {:success true
   :data (port/find-all (:repository usecase))})

(defn create-todo [usecase title description]
  (try
    (let [new-todo (domain/create-todo title description)]
      (if (domain/valid-todo? new-todo)
        (let [saved-todo (port/save (:repository usecase) new-todo)]
          {:success true
           :data saved-todo})
        {:success false
         :error "Invalid todo data"}))
    (catch Exception e
      {:success false
       :error (.getMessage e)})))
```

## 動的ディスパッチの仕組み

### Clojureのprotocolとdefrecordによる多態性

1. **Protocol定義**: インターフェースを定義
2. **defrecord実装**: 具体的な実装を提供
3. **動的ディスパッチ**: 実行時に適切なメソッドが自動選択

```clojure
;; これがキーポイント！
(defrecord InMemoryTodoRepository [data-store next-id]
  port/TodoRepository  ; ← protocolを実装
  
  (find-all [_]        ; ← 具体的な実装
    (vals @data-store)))
```

### 実行時の流れ

1. Usecaseが `(port/find-all repository)` を呼び出し
2. Clojureのprotocol機能が`repository`の型を確認
3. `InMemoryTodoRepository`の`find-all`実装が自動的に呼び出される

## 依存性注入（Integrant）

### 設定ファイル（config.edn）

```clojure
{:duct.profile/base
 {
  ;; Infrastructure Layer - Repository
  :todo.gateway/todo-repository {}

  ;; Application Layer - Usecase
  :todo.usecase/todo {:repository #ig/ref :todo.gateway/todo-repository}

  ;; Handler Layer - HTTP Endpoints
  :todo.handler/get-todos {:todo-usecase #ig/ref :todo.usecase/todo}
 }}
```

### Integrantコンポーネント

```clojure
;; Gateway
(defmethod ig/init-key :todo.gateway/todo-repository [_ options]
  (create-in-memory-repository))

;; Usecase
(defmethod ig/init-key :todo.usecase/todo [_ {:keys [repository]}]
  (->TodoUsecase repository))

;; Handler
(defmethod ig/init-key :todo.handler/get-todos [_ {:keys [todo-usecase]}]
  (fn [request] (get-todos-handler request todo-usecase)))
```

## 処理の詳細な流れ

### 例：Todo一覧取得 (`GET /api/todos`)

1. **HTTPリクエスト受信**
   ```
   GET /api/todos → Ataraxy Router
   ```

2. **Handler呼び出し**
   ```clojure
   (defn get-todos-handler [request todo-usecase]
     (let [result (usecase/get-all-todos todo-usecase)]
       ;; レスポンス生成
   ```

3. **Usecase実行**
   ```clojure
   (defn get-all-todos [usecase]
     {:success true
      :data (port/find-all (:repository usecase))})  ; ← ここでPort呼び出し
   ```

4. **Protocol動的ディスパッチ**
   ```clojure
   ;; port/find-all が呼ばれると...
   ;; Clojureが (:repository usecase) の型を確認
   ;; → InMemoryTodoRepository であることを発見
   ;; → InMemoryTodoRepositoryのfind-all実装を呼び出し
   ```

5. **Gateway実装実行**
   ```clojure
   (defrecord InMemoryTodoRepository [data-store next-id]
     port/TodoRepository
     
     (find-all [_]
       (vals @data-store)))  ; ← 実際のデータアクセス
   ```

6. **結果の返却**
   ```
   Gateway → Usecase → Handler → HTTPレスポンス
   ```

## メリット

### 1. テスタビリティ

```clojure
;; テスト用のモック実装
(defrecord MockTodoRepository [test-data]
  port/TodoRepository
  
  (find-all [_]
    @test-data)
  
  (save [_ todo]
    (swap! test-data conj todo)
    todo))

;; テストで使用
(def test-usecase 
  (->TodoUsecase (->MockTodoRepository (atom []))))
```

### 2. 実装の切り替え

```clojure
;; データベース実装への切り替え
(defrecord DatabaseTodoRepository [db-spec]
  port/TodoRepository
  
  (find-all [_]
    (jdbc/query db-spec ["SELECT * FROM todos"]))
  
  (save [_ todo]
    (jdbc/insert! db-spec :todos todo)))

;; 設定ファイルで切り替え
:todo.gateway/todo-repository {:type :database
                               :db-spec {...}}
```

### 3. ビジネスロジックの独立性

- Usecaseはデータの保存方法を知らない
- インメモリ、データベース、外部API、どれでも同じUsecaseコードで動作
- ビジネスルールがインフラストラクチャの詳細に影響されない

## まとめ

依存関係の逆転は以下の技術要素の組み合わせで実現されています：

1. **Clojureのprotocol**: インターフェース定義
2. **defrecord**: 具体的な実装
3. **動的ディスパッチ**: 実行時の適切なメソッド選択
4. **Integrant**: 依存性注入コンテナ
5. **設定ファイル**: 依存関係の宣言的定義

これにより、ビジネスロジックがインフラストラクチャの詳細に依存しない、保守性とテスタビリティの高いアーキテクチャが実現されています。 