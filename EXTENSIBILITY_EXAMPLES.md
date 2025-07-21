# 依存関係の逆転による拡張性の実例

## 概要

依存関係の逆転により、**注入する依存のみを変更**するだけで、様々なデータストアや実装に対応できます。ビジネスロジック（Usecase）やインターフェース（Port）は一切変更不要です。

依存関係の逆転 = 拡張性の源泉

1. ビジネスロジック（Usecase）: 抽象に依存するため変更不要
2. インターフェース（Port）: 安定した契約のため変更不要
3. 実装（Gateway）: 新しい実装を追加、設定で切り替え

**結果**: 技術的な変更がビジネス要件に影響せず、ビジネス要件の変更も技術実装に最小限の影響で済む、持続可能で拡張性の高いアーキテクチャが実現されます。
これこそが「Open/Closed Principle（拡張に対して開いている、修正に対して閉じている）」の実践例でもあります。

## 拡張性の実例

### 1. Redis実装の追加

```clojure
;; src/todo/gateway/redis_todo_repository.clj
(ns todo.gateway.redis-todo-repository
  (:require [todo.port.todo-repository :as port]
            [integrant.core :as ig]
            [taoensso.carmine :as car]))

(defrecord RedisTodoRepository [redis-conn]
  port/TodoRepository  ; ← 同じインターフェース！
  
  (find-all [_]
    (car/wcar redis-conn
      (let [keys (car/keys "todo:*")]
        (map #(car/get %) keys))))
  
  (find-by-id [_ id]
    (car/wcar redis-conn
      (car/get (str "todo:" id))))
  
  (save [_ todo]
    (let [id (or (:id todo) (car/wcar redis-conn (car/incr "todo:next-id")))
          todo-with-id (assoc todo :id id)]
      (car/wcar redis-conn
        (car/set (str "todo:" id) todo-with-id))
      todo-with-id))
  
  (delete [_ id]
    (let [todo (car/wcar redis-conn (car/get (str "todo:" id)))]
      (when todo
        (car/wcar redis-conn (car/del (str "todo:" id)))
        todo))))

;; 設定での使用
{:todo.gateway/redis-todo-repository {:redis-conn {...}}
 :todo.usecase/todo {:repository #ig/ref :todo.gateway/redis-todo-repository}}
```

### 2. 外部API実装の追加

```clojure
;; src/todo/gateway/api_todo_repository.clj
(ns todo.gateway.api-todo-repository
  (:require [todo.port.todo-repository :as port]
            [integrant.core :as ig]
            [clj-http.client :as http]
            [cheshire.core :as json]))

(defrecord ApiTodoRepository [base-url api-key]
  port/TodoRepository  ; ← 同じインターフェース！
  
  (find-all [_]
    (-> (http/get (str base-url "/todos")
                  {:headers {"Authorization" (str "Bearer " api-key)}})
        :body
        (json/parse-string true)))
  
  (save [_ todo]
    (-> (http/post (str base-url "/todos")
                   {:headers {"Authorization" (str "Bearer " api-key)
                              "Content-Type" "application/json"}
                    :body (json/generate-string todo)})
        :body
        (json/parse-string true))))

;; 設定での使用
{:todo.gateway/api-todo-repository {:base-url "https://api.example.com"
                                    :api-key #duct/env "API_KEY"}
 :todo.usecase/todo {:repository #ig/ref :todo.gateway/api-todo-repository}}
```

### 3. MongoDB実装の追加

```clojure
;; src/todo/gateway/mongo_todo_repository.clj
(ns todo.gateway.mongo-todo-repository
  (:require [todo.port.todo-repository :as port]
            [integrant.core :as ig]
            [monger.core :as mg]
            [monger.collection :as mc]))

(defrecord MongoTodoRepository [db]
  port/TodoRepository  ; ← 同じインターフェース！
  
  (find-all [_]
    (mc/find-maps db "todos"))
  
  (find-by-id [_ id]
    (mc/find-one-as-map db "todos" {:_id id}))
  
  (save [_ todo]
    (if (:_id todo)
      (mc/update db "todos" {:_id (:_id todo)} todo)
      (mc/insert-and-return db "todos" todo))))

;; 設定での使用
{:todo.gateway/mongo-todo-repository {:db #ig/ref :mongo/database}
 :todo.usecase/todo {:repository #ig/ref :todo.gateway/mongo-todo-repository}}
```

## 環境別の実装切り替え

### プロファイル別設定

```clojure
;; resources/todo/config.edn
{:duct.profile/base
 {;; 共通設定
  :todo.handler/get-todos {:todo-usecase #ig/ref :todo.usecase/todo}}

 ;; 開発環境：高速なインメモリ
 :duct.profile/dev
 {:todo.gateway/todo-repository {}
  :todo.usecase/todo {:repository #ig/ref :todo.gateway/todo-repository}}

 ;; ステージング環境：PostgreSQL
 :duct.profile/staging  
 {:duct.database/sql {:connection-uri #duct/env "STAGING_DATABASE_URL"}
  :todo.gateway/postgres-todo-repository {:db #ig/ref :duct.database/sql}
  :todo.usecase/todo {:repository #ig/ref :todo.gateway/postgres-todo-repository}}

 ;; 本番環境：Redis（高性能）
 :duct.profile/prod
 {:todo.gateway/redis-todo-repository {:redis-conn #ig/ref :redis/connection}
  :todo.usecase/todo {:repository #ig/ref :todo.gateway/redis-todo-repository}}

 ;; テスト環境：モック
 :duct.profile/test
 {:todo.gateway/mock-todo-repository {}
  :todo.usecase/todo {:repository #ig/ref :todo.gateway/mock-todo-repository}}}
```

## テスト用モック実装

```clojure
;; test/todo/gateway/mock_todo_repository.clj
(defrecord MockTodoRepository [test-data expected-calls]
  port/TodoRepository
  
  (find-all [_]
    (swap! expected-calls conj [:find-all])
    @test-data)
  
  (save [_ todo]
    (swap! expected-calls conj [:save todo])
    (let [new-todo (assoc todo :id (inc (count @test-data)))]
      (swap! test-data conj new-todo)
      new-todo))
  
  (find-by-id [_ id]
    (swap! expected-calls conj [:find-by-id id])
    (first (filter #(= (:id %) id) @test-data))))

;; テストでの使用
(deftest test-create-todo
  (let [mock-repo (->MockTodoRepository (atom []) (atom []))
        usecase (->TodoUsecase mock-repo)
        result (create-todo usecase "Test" "Description")]
    
    (is (:success result))
    (is (= [[:save {:title "Test" :description "Description"}]] 
           @(:expected-calls mock-repo)))))
```

## 複合実装（複数データストアの組み合わせ）

```clojure
;; 読み込み：Redis（高速）、書き込み：PostgreSQL（永続化）
(defrecord HybridTodoRepository [redis-repo postgres-repo]
  port/TodoRepository
  
  (find-all [_]
    ;; まずRedisから取得を試行
    (let [cached (port/find-all redis-repo)]
      (if (seq cached)
        cached
        ;; キャッシュミスの場合はPostgreSQLから取得してRedisにキャッシュ
        (let [todos (port/find-all postgres-repo)]
          (doseq [todo todos]
            (port/save redis-repo todo))
          todos))))
  
  (save [_ todo]
    ;; PostgreSQLに永続化
    (let [saved-todo (port/save postgres-repo todo)]
      ;; Redisにもキャッシュ
      (port/save redis-repo saved-todo)
      saved-todo)))

;; 設定
{:todo.gateway/hybrid-todo-repository 
 {:redis-repo #ig/ref :todo.gateway/redis-todo-repository
  :postgres-repo #ig/ref :todo.gateway/postgres-todo-repository}
 :todo.usecase/todo {:repository #ig/ref :todo.gateway/hybrid-todo-repository}}
```

## バージョン管理付き実装

```clojure
;; 履歴管理機能付きリポジトリ
(defrecord VersionedTodoRepository [current-repo history-repo]
  port/TodoRepository
  
  (save [_ todo]
    ;; 現在のバージョンを履歴に保存
    (when-let [existing (port/find-by-id current-repo (:id todo))]
      (port/save history-repo (assoc existing 
                                     :version-id (java.util.UUID/randomUUID)
                                     :archived-at (java.time.Instant/now))))
    ;; 新しいバージョンを保存
    (port/save current-repo todo)))
```

## 設定による動的切り替え

```clojure
;; 実行時の動的切り替え
(defn create-repository [config]
  (case (:type config)
    :memory (create-in-memory-repository)
    :postgres (create-postgres-repository (:db-spec config))
    :redis (create-redis-repository (:redis-conn config))
    :api (create-api-repository (:base-url config) (:api-key config))
    :hybrid (->HybridTodoRepository 
             (create-redis-repository (:redis-conn config))
             (create-postgres-repository (:db-spec config)))))

;; 設定ファイル
{:repository {:type :hybrid
              :redis-conn {...}
              :db-spec {...}}}
```

## 拡張性のメリット

### 1. **技術的負債の軽減**
- 新技術への移行が容易
- 段階的な移行が可能
- ビジネスロジックへの影響なし

### 2. **パフォーマンス最適化**
- 用途別の最適なデータストア選択
- 複合実装による性能向上
- キャッシュ戦略の柔軟な実装

### 3. **テスタビリティ**
- 高速なインメモリテスト
- モックを使った単体テスト
- 実データを使った統合テスト

### 4. **運用の柔軟性**
- 環境別の実装切り替え
- A/Bテストでの実装比較
- 段階的なロールアウト

## まとめ

依存関係の逆転により：

```
変更が必要な部分：
├── Gateway実装（新しいデータストア対応）
└── 設定ファイル（注入する依存の指定）

変更が不要な部分：
├── Port（インターフェース）
├── Usecase（ビジネスロジック）  
├── Handler（HTTP処理）
└── Domain（ドメインモデル）
```

**本質**: ビジネスロジックがインフラストラクチャの詳細に依存しないため、技術的な変更がビジネス要件に影響せず、逆もまた真です。これにより、持続可能で拡張性の高いシステムアーキテクチャが実現されます。 