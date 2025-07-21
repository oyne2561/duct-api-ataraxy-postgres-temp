# インメモリからPostgreSQLへの移行ガイド

## 概要

依存関係の逆転のおかげで、**Usecase、Port、Handlerは一切変更せずに**、データ永続化層だけを変更してPostgreSQLを使用できます。

## 変更が必要なファイル

### ✅ 変更が必要（新規作成/修正）
1. `src/todo/gateway/postgres_todo_repository.clj` - 新規作成
2. `resources/migrations/001-create-todos.up.sql` - 新規作成
3. `resources/migrations/001-create-todos.down.sql` - 新規作成
4. `resources/todo/postgres-config.edn` - 新規作成（または既存設定を修正）

### ❌ 変更が不要（依存関係の逆転のメリット）
1. `src/todo/port/todo_repository.clj` - **変更不要**
2. `src/todo/usecase/todo.clj` - **変更不要**
3. `src/todo/handler/todo.clj` - **変更不要**
4. `src/todo/domain/todo.clj` - **変更不要**

## 変更内容の詳細

### 1. PostgreSQL Gateway実装

```clojure
;; src/todo/gateway/postgres_todo_repository.clj
(defrecord PostgresTodoRepository [db-spec]
  port/TodoRepository  ; ← 同じインターフェースを実装
  
  (find-all [_]
    (jdbc/query db-spec
                ["SELECT id, title, description, completed, created_at, updated_at 
                  FROM todos ORDER BY created_at DESC"]
                {:row-fn row-mapper}))
  
  ;; その他のメソッドも同様にSQL実装...
)
```

**ポイント**: 
- `port/TodoRepository`と同じインターフェースを実装
- データアクセスの詳細のみ変更（インメモリ → SQL）
- Usecaseから見ると全く同じインターフェース

### 2. データベーススキーマ

```sql
-- resources/migrations/001-create-todos.up.sql
CREATE TABLE todos (
  id SERIAL PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 3. 設定ファイルでの切り替え

**変更前（インメモリ）**:
```clojure
{:todo.gateway/todo-repository {}
 :todo.usecase/todo {:repository #ig/ref :todo.gateway/todo-repository}}
```

**変更後（PostgreSQL）**:
```clojure
{:duct.database/sql {:connection-uri "jdbc:postgresql://..."}
 :todo.gateway/postgres-todo-repository {:db #ig/ref :duct.database/sql}
 :todo.usecase/todo {:repository #ig/ref :todo.gateway/postgres-todo-repository}}
```

**ポイント**: 
- 参照先を変更するだけ
- Usecaseの設定は実装の変更のみ

## 実際の処理の流れ比較

### インメモリ版
```
Handler → Usecase → Port → InMemoryTodoRepository → Atom
```

### PostgreSQL版
```
Handler → Usecase → Port → PostgresTodoRepository → PostgreSQL
```

**重要**: `Handler → Usecase → Port` の部分は**完全に同じ**！

## 動的ディスパッチの仕組み

### 1. 実行時の型解決

```clojure
;; Usecaseのコード（変更なし）
(defn get-all-todos [usecase]
  {:success true
   :data (port/find-all (:repository usecase))})  ; ← この呼び出しは同じ
```

### 2. Clojureが適切な実装を選択

**インメモリ版**:
```clojure
;; repository = InMemoryTodoRepository
(port/find-all repository) 
;; → InMemoryTodoRepositoryのfind-all実装が呼ばれる
;; → (vals @data-store)
```

**PostgreSQL版**:
```clojure
;; repository = PostgresTodoRepository  
(port/find-all repository)
;; → PostgresTodoRepositoryのfind-all実装が呼ばれる
;; → (jdbc/query db-spec ["SELECT ..."])
```

## セットアップ手順

### 1. PostgreSQLデータベースを準備

```bash
# PostgreSQLサーバーが動いていることを確認
sudo service postgresql start

# データベースとユーザーを作成
sudo -u postgres psql
CREATE DATABASE todo_db;
CREATE USER todo_user WITH PASSWORD 'todo_pass';
GRANT ALL PRIVILEGES ON DATABASE todo_db TO todo_user;
\q
```

### 2. 環境変数を設定

```bash
# .env ファイルまたは環境変数
export DATABASE_URL="jdbc:postgresql://localhost:5432/todo_db?user=todo_user&password=todo_pass"
```

### 3. マイグレーション実行

```bash
# Ductでマイグレーション実行
lein run :duct/migrator
```

### 4. 設定ファイルを切り替え

```bash
# postgres-config.ednを使用
lein run :duct.config/read :postgres-config.edn
```

## 両実装の併用

依存関係の逆転により、実装を簡単に切り替えられます：

### 設定による切り替え

```clojure
;; 開発環境：インメモリ
:duct.profile/dev
{:todo.usecase/todo {:repository #ig/ref :todo.gateway/todo-repository}}

;; 本番環境：PostgreSQL  
:duct.profile/prod
{:todo.usecase/todo {:repository #ig/ref :todo.gateway/postgres-todo-repository}}
```

### 実行時の切り替え

```clojure
;; コードでの動的切り替えも可能
(defn create-usecase [env]
  (case env
    :dev (->TodoUsecase (create-in-memory-repository))
    :prod (->TodoUsecase (create-postgres-repository db-spec))))
```

## テストでのメリット

### インメモリでの高速テスト

```clojure
(deftest test-create-todo
  (let [usecase (->TodoUsecase (create-in-memory-repository))
        result (create-todo usecase "Test Todo" "Description")]
    (is (:success result))))
```

### PostgreSQLでの統合テスト

```clojure
(deftest integration-test-create-todo
  (let [usecase (->TodoUsecase (create-postgres-repository test-db-spec))
        result (create-todo usecase "Test Todo" "Description")]
    (is (:success result))))
```

**同じテストコードが両方の実装で動作**！

## まとめ

依存関係の逆転により：

1. **ビジネスロジックの保護**: Usecaseはデータストレージの詳細を知らない
2. **柔軟な実装切り替え**: 設定ファイルの変更だけで切り替え可能
3. **テスタビリティ**: インメモリでの高速テスト、本番環境での統合テスト
4. **保守性**: 新しいストレージ（Redis、MongoDB等）への対応が容易

**本質的なポイント**: インフラストラクチャ（Gateway）がビジネスロジック（Usecase）のインターフェース（Port）に依存する構造により、実装の詳細変更がアプリケーションコアに影響しない。 