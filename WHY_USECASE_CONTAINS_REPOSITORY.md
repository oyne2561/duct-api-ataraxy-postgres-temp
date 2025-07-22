# なぜTodoUsecaseでrepositoryを内包するのか？

## 🤔 質問
「なぜTodoUsecaseでrepositoryを内包する必要があるの？」

## 📝 回答

TodoUsecaseでrepositoryを内包する理由は、**クリーンアーキテクチャの重要な設計原則**を実現するためです。

## 🏗️ 設計原則

### 1. **依存性逆転の原則（Dependency Inversion Principle）**

#### ❌ もしrepositoryを内包しない場合
```clojure
;; BAD: 直接データベースアクセス
(defn get-all-todos []
  (jdbc/query database-connection ["SELECT * FROM todos"]))

;; BAD: グローバル状態に依存
(def global-repository (atom {}))
(defn get-all-todos []
  (vals @global-repository))
```

**問題点:**
- ビジネスロジックがインフラ（DB）の詳細に直接依存
- テストが困難（実際のDBが必要）
- 実装変更時にビジネスロジックも修正が必要

#### ✅ repository内包による解決
```clojure
;; GOOD: インターフェースに依存
(defrecord TodoUsecase [repository])

(defn get-all-todos [usecase]
  {:success true
   :data (port/find-all (:repository usecase))})  ; インターフェース経由
```

**メリット:**
- ビジネスロジックがインターフェースにのみ依存
- 具体的なデータアクセス方法を知らない
- 実装を自由に切り替え可能

### 2. **単一責任の原則（Single Responsibility Principle）**

```clojure
;; Usecaseの責務: ビジネスフローの制御
(defn create-todo [usecase title description]
  (try
    (let [new-todo (domain/create-todo title description)]     ; 1. ドメインオブジェクト作成
      (if (domain/valid-todo? new-todo)                        ; 2. バリデーション
        (let [saved-todo (port/save (:repository usecase) new-todo)]  ; 3. 保存（委譲）
          {:success true :data saved-todo})
        {:success false :error "Invalid todo data"}))
    (catch Exception e
      {:success false :error (.getMessage e)})))              ; 4. エラーハンドリング

;; Repositoryの責務: データアクセス
(defn save [repository todo]
  ;; データベースまたはインメモリストレージへの保存詳細
  )
```

### 3. **テスタビリティの向上**

#### テスト用モック実装
```clojure
;; テスト専用のRepository実装
(defrecord TestTodoRepository [test-data]
  port/TodoRepository
  
  (find-all [_]
    @test-data)
  
  (save [_ todo]
    (swap! test-data conj todo)
    todo)
  
  (delete [_ id]
    (swap! test-data #(remove (fn [todo] (= (:id todo) id)) %))))

;; テストでの使用
(deftest create-todo-test
  (let [test-repo (->TestTodoRepository (atom []))
        test-usecase (->TodoUsecase test-repo)
        result (create-todo test-usecase "テストタスク" "説明")]
    (is (= true (:success result)))
    (is (= "テストタスク" (-> result :data :title)))))
```

### 4. **実装の切り替え可能性**

```clojure
;; 設定ファイル（config.edn）で実装を切り替え
{:duct.profile/base
 {
  ;; 開発環境: インメモリ
  :todo.gateway/todo-repository {}
  :todo.usecase/todo {:repository #ig/ref :todo.gateway/todo-repository}
 }

 :duct.profile/prod
 {
  ;; 本番環境: PostgreSQL  
  :todo.gateway/postgres-todo-repository {:db #ig/ref :duct.database/sql}
  :todo.usecase/todo {:repository #ig/ref :todo.gateway/postgres-todo-repository}
 }}
```

**同じUsecaseコードで**:
- 開発環境: インメモリ実装
- 本番環境: PostgreSQL実装
- テスト環境: モック実装

### 5. **関数の純粋性と予測可能性**

#### ❌ グローバル状態に依存
```clojure
(def global-repository (atom {}))

(defn get-todo-by-id [id]
  ;; グローバル状態に依存 = 副作用あり
  (get @global-repository id))
```

**問題:**
- 関数の結果がグローバル状態に依存
- テストでの状態制御が困難
- 並行処理時の競合状態

#### ✅ 引数として明示的に受け取る
```clojure
(defn get-todo-by-id [usecase id]
  ;; 必要な依存関係が引数で明示的
  (if-let [todo (port/find-by-id (:repository usecase) id)]
    {:success true :data todo}
    {:success false :error "Todo not found"}))
```

**メリット:**
- 関数の依存関係が明確
- テスト時に必要な依存関係を制御可能
- 関数の動作が予測可能

## 🔄 実際のデータフロー

### HTTP リクエストから処理まで

```
1. HTTPリクエスト
   ↓
2. Handler（リクエスト解析）
   ↓
3. Usecase（ビジネスロジック実行）
   ├─ ドメインオブジェクト操作
   ├─ バリデーション実行
   └─ Repository呼び出し（データアクセス）
   ↓
4. Repository（データ永続化/取得）
   ↓
5. レスポンス生成
```

### コード例での流れ
```clojure
;; 1. Handler
(defn create-todo-handler [request todo-usecase]
  (let [title (get-in request [:params :title])
        description (get-in request [:params :description])
        result (usecase/create-todo todo-usecase title description)]  ; 2. Usecase呼び出し
    {:status 200 :body result}))

;; 2. Usecase（repositoryを内包）
(defn create-todo [usecase title description]
  (let [new-todo (domain/create-todo title description)]          ; 3. ドメイン操作
    (if (domain/valid-todo? new-todo)                            ; 4. バリデーション
      (let [saved-todo (port/save (:repository usecase) new-todo)]  ; 5. Repository呼び出し
        {:success true :data saved-todo})
      {:success false :error "Invalid todo data"})))

;; 3. Repository（具体的な実装）
(defn save [repository todo]
  (swap! (:data-store repository) assoc (:id todo) todo))       ; 6. 実際のデータ保存
```

## 🎯 まとめ：なぜ内包が必要か

### **理由1: アーキテクチャの一貫性**
- Usecaseはビジネスロジック層の責務を持つ
- データアクセスの詳細を知らず、インターフェース経由で操作

### **理由2: 柔軟性の確保**
- 実装を切り替えてもUsecaseコードは不変
- 新しいデータストア対応が容易

### **理由3: テストの容易さ**
- モック/スタブを簡単に注入可能
- ビジネスロジックを独立してテスト可能

### **理由4: 保守性の向上**
- 責務が明確に分離されている
- 変更の影響範囲が限定される

### **理由5: 関数型プログラミングの原則**
- 依存関係が明示的
- 副作用が制御されている
- 関数の動作が予測可能

## 🔍 変数 `repository` と `usecase` の具体的な違い

### **1. 型（Type）の違い**

```clojure
;; repository変数の型
repository = #todo.gateway.todo_repository.InMemoryTodoRepository{
  :data-store #<Atom@123 {1 {:id 1, :title "Clojureを学習する", :description "関数型プログラミングの基本を理解する", :completed false, :created-at #inst"2024-01-01T10:00:00.000Z", :updated-at #inst"2024-01-01T10:00:00.000Z"}
                          2 {:id 2, :title "TODOアプリを作成する", :description "クリーンアーキテクチャで実装する", :completed false, :created-at #inst"2024-01-02T09:00:00.000Z", :updated-at #inst"2024-01-02T09:00:00.000Z"}
                          3 {:id 3, :title "ドキュメントを読む", :description "Ductフレームワークのドキュメントを確認", :completed true, :created-at #inst"2024-01-01T08:00:00.000Z", :updated-at #inst"2024-01-01T15:00:00.000Z"}}>
  :next-id #<Atom@456 3>
}

;; usecase変数の型  
usecase = #todo.usecase.todo.TodoUsecase{
  :repository #todo.gateway.todo_repository.InMemoryTodoRepository{
    :data-store #<Atom@123 {1 {:id 1, :title "Clojureを学習する", :completed false, ...}
                            2 {:id 2, :title "TODOアプリを作成する", :completed false, ...}
                            3 {:id 3, :title "ドキュメントを読む", :completed true, ...}}>
    :next-id #<Atom@456 3>
  }
}
```

### **2. 責務（Responsibility）の違い**

| 変数 | 責務 | 具体的な処理 |
|------|------|------------|
| `repository` | **データアクセス** | ・データの永続化<br>・データの取得<br>・データの削除<br>・データの検索 |
| `usecase` | **ビジネスロジック** | ・業務フローの制御<br>・バリデーション<br>・エラーハンドリング<br>・トランザクション管理 |

### **3. メソッド（操作）の違い**

#### repository変数が持つメソッド
```clojure
;; データアクセス専用の操作
(port/find-all repository)           ; 全データ取得
(port/find-by-id repository 1)       ; ID検索
(port/save repository todo)          ; データ保存
(port/delete repository 1)           ; データ削除
(port/count-todos repository)        ; 件数取得
```

#### usecase変数が持つメソッド
```clojure
;; ビジネスロジック操作
(get-all-todos usecase)              ; 全Todo取得（+ビジネスルール）
(create-todo usecase title desc)     ; Todo作成（+バリデーション）
(update-todo usecase id updates)     ; Todo更新（+権限チェック）
(toggle-completion usecase id)       ; 完了切替（+状態管理）
```

### **4. 内部構造の違い**

#### repository変数の内部
```clojure
;; インメモリ実装の場合
{:data-store (atom {1 {:id 1, :title "Clojureを学習する", :description "関数型プログラミングの基本を理解する", :completed false, :created-at #inst"2024-01-01T10:00:00.000Z", :updated-at #inst"2024-01-01T10:00:00.000Z"}
                    2 {:id 2, :title "TODOアプリを作成する", :description "クリーンアーキテクチャで実装する", :completed false, :created-at #inst"2024-01-02T09:00:00.000Z", :updated-at #inst"2024-01-02T09:00:00.000Z"}
                    3 {:id 3, :title "ドキュメントを読む", :description "Ductフレームワークのドキュメントを確認", :completed true, :created-at #inst"2024-01-01T08:00:00.000Z", :updated-at #inst"2024-01-01T15:00:00.000Z"}})
 :next-id (atom 3)}

;; PostgreSQL実装の場合
{:db-spec {:subprotocol "postgresql"
           :subname "//localhost:5432/todo_db"
           :user "todo_user"
           :password "todo_pass"}}
```

#### usecase変数の内部
```clojure
;; 常に同じ構造（実装に依存しない）
{:repository #<Repository実装オブジェクト>}
```

### **5. ライフサイクルの違い**

#### repository変数
```clojure
;; Integrantによる初期化
(defmethod ig/init-key :todo.gateway/todo-repository [_ options]
  (create-in-memory-repository))  ; Repository作成

;; 使用例
(let [repo (create-in-memory-repository)]
  (port/save repo todo))  ; 直接データアクセス
```

#### usecase変数
```clojure
;; Integrantによる初期化（repositoryを注入）
(defmethod ig/init-key :todo.usecase/todo [_ {:keys [repository]}]
  (->TodoUsecase repository))  ; Usecaseにrepository注入

;; 使用例
(let [usecase (->TodoUsecase repository)]
  (create-todo usecase "TypeScriptを学習する" "型安全性と実用的な設計パターンを習得"))  ; ビジネスロジック実行
```

### **6. 抽象化レベルの違い**

#### repository変数（低レベル）
```clojure
;; 具体的なデータ操作
(defn save [repository todo]
  (let [id (or (:id todo) (swap! (:next-id repository) inc))]
    (swap! (:data-store repository) assoc id todo)))
```

#### usecase変数（高レベル）
```clojure
;; ビジネス価値のある操作
(defn create-todo [usecase title description]
  (let [new-todo (domain/create-todo title description)]    ; ドメインロジック
    (if (domain/valid-todo? new-todo)                      ; ビジネスルール
      (port/save (:repository usecase) new-todo)           ; データ操作委譲
      {:success false :error "Invalid data"})))            ; エラーハンドリング
```

### **7. テスト方法の違い**

#### repository変数のテスト
```clojure
;; データアクセスのテスト
(deftest repository-save-test
  (let [repo (create-test-repository)]
    (let [saved (port/save repo {:title "データベース設計を学ぶ" :description "正規化とインデックス最適化"})]
      (is (= "データベース設計を学ぶ" (:title saved)))
      (is (number? (:id saved))))))
```

#### usecase変数のテスト
```clojure
;; ビジネスロジックのテスト
(deftest usecase-create-todo-test
  (let [mock-repo (create-mock-repository)
        usecase (->TodoUsecase mock-repo)]
    (let [result (create-todo usecase "Clojureテストを書く" "ユニットテストとモックの実装")]
      (is (= true (:success result)))
      (is (= "Clojureテストを書く" (-> result :data :title))))))
```

### **8. エラーハンドリングの違い**

#### repository変数
```clojure
;; 技術的なエラー（データベース接続エラーなど）
(defn save [repository todo]
  (try
    (jdbc/insert! (:db-spec repository) :todos todo)
    (catch SQLException e
      (throw (ex-info "Database error" {:cause e})))))
```

#### usecase変数
```clojure
;; ビジネス的なエラー（バリデーションエラーなど）
(defn create-todo [usecase title description]
  (try
    (let [new-todo (domain/create-todo title description)]
      (if (domain/valid-todo? new-todo)
        {:success true :data (port/save (:repository usecase) new-todo)}
        {:success false :error "Invalid todo data" :error-code :validation-error}))
    (catch Exception e
      {:success false :error (.getMessage e) :error-code :internal-error})))
```

## 🚀 結論

### **repository変数**
- **データアクセス層**の実装オブジェクト
- **技術的な詳細**を扱う
- **永続化方法**に特化

### **usecase変数**
- **ビジネスロジック層**のオブジェクト
- **業務価値**を提供する
- **ビジネスフロー**を制御

TodoUsecaseでrepositoryを内包することで、**「ビジネスロジック」と「データアクセス」の責務を分離**し、**依存関係を明示的に管理**できます。これにより、保守性、テスタビリティ、拡張性の高いアプリケーションが実現されるのです。

つまり、**「内包」は単なる技術的な実装詳細ではなく、優れたソフトウェア設計の基礎となる重要な仕組み**なのです！ 