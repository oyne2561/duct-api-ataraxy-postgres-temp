# 引数 `repository` と `usecase` の実体について

このドキュメントでは、TodoアプリケーションのUsecaseレイヤーで使用される引数 `repository` と `usecase` が実際に何であるかを詳しく説明します。

## `repository` 引数の実体

### 概要
`repository` は **TodoRepositoryプロトコルを実装したレコードのインスタンス** です。

### プロトコル定義
```clojure
;; src/todo/port/todo_repository.clj
(defprotocol TodoRepository
  "Todoエンティティのデータアクセスを抽象化するプロトコル"
  
  (find-all [this])
  (find-by-id [this id])
  (find-by-status [this completed])
  (save [this todo])
  (delete [this id])
  (count-todos [this])
  (count-completed [this]))
```

### 具体的な実装

#### 1. インメモリ実装 (`InMemoryTodoRepository`)
```clojure
;; src/todo/gateway/todo_repository.clj
(defrecord InMemoryTodoRepository [data-store next-id]
  port/TodoRepository
  ;; プロトコルの実装...
)
```

**構造:**
- `data-store`: `atom` - Todoデータを格納するマップ `{id todo-map}`
- `next-id`: `atom` - 次に割り当てるIDの値

**実際の中身例:**
```clojure
{:data-store #<Atom@123 {1 {:id 1 :title "学習" :completed false ...}
                         2 {:id 2 :title "開発" :completed true ...}}>
 :next-id #<Atom@456 3>}
```

#### 2. PostgreSQL実装 (`PostgresTodoRepository`)
```clojure
;; src/todo/gateway/postgres_todo_repository.clj
(defrecord PostgresTodoRepository [db-spec]
  port/TodoRepository
  ;; プロトコルの実装...
)
```

**構造:**
- `db-spec`: データベース接続情報を含むマップ

**実際の中身例:**
```clojure
{:db-spec {:subprotocol "postgresql"
           :subname "//localhost:5432/todo_db"
           :user "todo_user"
           :password "todo_pass"}}
```

## `usecase` 引数の実体

### 概要
`usecase` は **TodoUsecaseレコードのインスタンス** です。

### レコード定義
```clojure
;; src/todo/usecase/todo.clj
(defrecord TodoUsecase [repository])
```

**構造:**
- `repository`: 上述のTodoRepositoryプロトコルを実装したオブジェクト

**実際の中身例:**
```clojure
#todo.usecase.todo.TodoUsecase{
  :repository #todo.gateway.todo_repository.InMemoryTodoRepository{
    :data-store #<Atom@123 {1 {:id 1 :title "学習" ...}}>,
    :next-id #<Atom@456 3>
  }
}
```

## データフローの実例

### 1. `get-todo-by-id` 関数の実行時

```clojure
(defn get-todo-by-id [usecase id]
  (if-let [todo (port/find-by-id (:repository usecase) id)]
    {:success true :data todo}
    {:success false :error "Todo not found"}))
```

**実行時の値:**
1. `usecase` = `TodoUsecaseレコード`
2. `(:repository usecase)` = `InMemoryTodoRepository` または `PostgresTodoRepository`
3. `(port/find-by-id repository id)` = プロトコルメソッドの呼び出し

### 2. `create-todo` 関数の実行時

```clojure
(defn create-todo [usecase title description]
  (let [new-todo (domain/create-todo title description)]
    ;; ... validation ...
    (let [saved-todo (port/save (:repository usecase) new-todo)]
      {:success true :data saved-todo})))
```

**実行時の値:**
1. `new-todo` = `{:id nil :title "新しいタスク" :description "説明" :completed false :created-at #inst... :updated-at #inst...}`
2. `port/save` がrepositoryの実装に応じて:
   - インメモリ版: atomにデータを保存
   - PostgreSQL版: データベースにINSERT

## Integrantによる依存性注入

### 設定ファイル (`resources/todo/config.edn`)
```clojure
;; リポジトリの定義
:todo.gateway/todo-repository {}

;; ユースケースの定義（リポジトリを注入）
:todo.usecase/todo {:repository #ig/ref :todo.gateway/todo-repository}
```

### 初期化処理
```clojure
;; リポジトリの初期化
(defmethod ig/init-key :todo.gateway/todo-repository [_ options]
  (create-in-memory-repository))

;; ユースケースの初期化
(defmethod ig/init-key :todo.usecase/todo [_ {:keys [repository]}]
  (->TodoUsecase repository))
```

## まとめ

- **`repository`**: データアクセス層の実装オブジェクト（インメモリまたはPostgreSQL）
- **`usecase`**: ビジネスロジック層のオブジェクト（repositoryを内包）
- 両方ともClojureのレコード（`defrecord`）で定義された構造体
- Integrantフレームワークによって依存性注入され、実行時に具体的なインスタンスとして存在
- プロトコルベースの設計により、実装を切り替え可能（インメモリ ⇔ PostgreSQL） 