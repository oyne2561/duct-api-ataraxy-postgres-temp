# Todo アプリケーション - クリーンアーキテクチャ指示書

## 📋 概要

本プロジェクトは、Clojure/Ductフレームワークを使用したTodoアプリケーションで、クリーンアーキテクチャ（ヘキサゴナルアーキテクチャ）を採用しています。

## 🏗️ アーキテクチャ概要

```
Handler → Usecase → Domain
              ↓        ↓
            Gateway ← Port
```

### 依存関係の方向
- 外側の層は内側の層に依存する
- 内側の層は外側の層に依存しない
- インターフェース（Port）を通じて依存性を逆転

## 📁 ディレクトリ構成

```
src/todo/
├── domain/              # ドメイン層
│   └── todo.clj         # Todoエンティティとビジネスロジック
├── port/                # ポート層（インターフェース）
│   └── todo_repository.clj  # リポジトリのプロトコル定義
├── gateway/             # ゲートウェイ層（外部接続）
│   └── todo_repository.clj  # リポジトリの実装
├── usecase/             # ユースケース層
│   └── todo.clj         # アプリケーションロジック
└── handler/             # ハンドラー層（HTTP）
    ├── example.clj      # サンプルハンドラー
    └── todo.clj         # Todoエンドポイント処理
```

## 🎯 各層の責務

### Domain層 (`domain/`)
**責務**: ビジネスロジックとエンティティ
- Todoエンティティの定義
- ドメインルールの実装
- バリデーション関数
- ビジネス不変条件の保証

**ファイル**: `domain/todo.clj`
- `create-todo`: Todo作成
- `complete-todo`: Todo完了
- `uncomplete-todo`: Todo未完了に戻す
- `update-todo-title`: タイトル更新
- `update-todo-description`: 説明更新
- `valid-todo?`: バリデーション

### Port層 (`port/`)
**責務**: インターフェース定義（契約）
- 外部システムとの契約を定義
- 依存性逆転の実現

**ファイル**: `port/todo_repository.clj`
- `TodoRepository`プロトコル定義
- データアクセスメソッドの仕様

### Gateway層 (`gateway/`)
**責務**: 外部システムとの実際の接続
- データベース、外部API、ファイルシステムなどとの通信
- Portインターフェースの実装

**ファイル**: `gateway/todo_repository.clj`
- `InMemoryTodoRepository`: インメモリ実装
- サンプルデータの提供
- Integrantコンポーネント設定

### Usecase層 (`usecase/`)
**責務**: アプリケーションロジック
- ビジネスフローの調整
- トランザクション境界
- エラーハンドリング

**ファイル**: `usecase/todo.clj`
- CRUD操作の実装
- ビジネスフロー制御
- 統一されたレスポンス形式

### Handler層 (`handler/`)
**責務**: 外部インターフェースとの接続
- HTTP リクエスト/レスポンス処理
- JSONパース/シリアライズ
- ルーティング

**ファイル**: `handler/todo.clj`
- HTTPエンドポイントの実装
- リクエスト検証
- レスポンス生成

## 🔌 APIエンドポイント

### Todo管理API

| メソッド | パス | 説明 | リクエスト例 |
|---------|------|------|------------|
| GET | `/api/todos` | 全Todo取得 | - |
| POST | `/api/todos` | Todo作成 | `{"title": "新しいタスク", "description": "説明"}` |
| GET | `/api/todos/stats` | Todo統計 | - |
| GET | `/api/todos/status?completed=true` | 完了状態でフィルタ | - |
| GET | `/api/todos/1` | 指定IDのTodo取得 | - |
| PUT | `/api/todos/1` | Todo更新 | `{"title": "更新タスク"}` |
| POST | `/api/todos/1/toggle` | 完了状態切り替え | - |
| DELETE | `/api/todos/1` | Todo削除 | - |

### レスポンス形式

#### 成功レスポンス
```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "Clojureを学習する",
    "description": "関数型プログラミングの基本を理解する",
    "completed": false,
    "created-at": "2024-01-01T10:00:00Z",
    "updated-at": "2024-01-01T10:00:00Z"
  }
}
```

#### エラーレスポンス
```json
{
  "error": "Todo not found",
  "error-code": "not-found"
}
```

## 🚀 使用方法

### 開発環境起動

1. **REPLを起動**
   ```bash
   lein repl
   ```

2. **開発環境読み込み**
   ```clojure
   (dev)
   ```

3. **サーバー起動**
   ```clojure
   (go)
   ```

4. **リロード**
   ```clojure
   (reset)
   ```

### API実行例

```bash
# 全Todo取得
curl http://localhost:3000/api/todos

# Todo作成
curl -X POST http://localhost:3000/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"新しいタスク","description":"説明"}'

# Todo完了切り替え
curl -X POST http://localhost:3000/api/todos/1/toggle
```

## ⚙️ 設定情報

### Integrant設定 (`resources/todo/config.edn`)

```clojure
{:duct.profile/base
 {;; Infrastructure Layer
  :todo.gateway/todo-repository {}
  
  ;; Application Layer  
  :todo.usecase/todo {:repository #ig/ref :todo.gateway/todo-repository}
  
  ;; Handler Layer
  :todo.handler/get-todos {:todo-usecase #ig/ref :todo.usecase/todo}
  ;; ... 他のハンドラー
  }}
```

### 依存関係 (`project.clj`)

```clojure
:dependencies [
  [org.clojure/clojure "1.10.3"]
  [duct/core "0.8.0"]
  [duct/module.ataraxy "0.3.0"]
  [duct/module.logging "0.5.0"]
  [duct/module.web "0.7.3"]
  [org.clojure/data.json "2.4.0"]
]
```

## 🔄 データフロー

### リクエスト処理フロー
1. **HTTP Request** → Handler層
2. **Handler** → Usecase層（ビジネスロジック実行）
3. **Usecase** → Domain層（エンティティ操作）
4. **Usecase** → Port → Gateway → データストア
5. **Response** ← Handler（JSON形式で返却）

### 例: Todo作成フロー
```
POST /api/todos 
→ create-todo-handler
→ create-todo usecase
→ domain/create-todo
→ port/save
→ gateway/InMemoryRepository
→ response
```

## 📈 拡張方針

### データベース接続
1. `gateway/`に新しい実装を追加
   - PostgreSQL実装
   - MongoDB実装
   - etc.

2. `config.edn`で実装を切り替え

### 外部API連携
1. `driver/`ディレクトリを作成
2. 外部API用のクライアント実装
3. Usecaseから利用

### 認証・認可
1. `domain/user.clj`でUserエンティティ
2. `middleware/auth.clj`で認証処理
3. Handler層で認証チェック

## 🧪 テスト戦略

### 単体テスト
- **Domain**: ビジネスロジックのテスト
- **Usecase**: アプリケーションフローのテスト
- **Gateway**: データアクセスのテスト

### 統合テスト
- **Handler**: エンドポイントのテスト
- **Full Stack**: リクエストからレスポンスまで

## 📝 コーディング規約

### 命名規則
- ファイル名: `kebab-case`
- 関数名: `kebab-case`
- プロトコル名: `PascalCase`
- レコード名: `PascalCase`

### 名前空間構成
```clojure
todo.domain.todo          ; ドメインエンティティ
todo.port.todo-repository  ; インターフェース
todo.gateway.todo-repository ; 実装
todo.usecase.todo         ; ユースケース
todo.handler.todo         ; HTTPハンドラー
```

## 🔧 トラブルシューティング

### よくある問題

1. **REPLエラー**
   - `(dev)` → `(reset)` で再読み込み
   - 構文エラーは個別ファイルをチェック

2. **依存関係エラー**
   - `project.clj`の依存関係を確認
   - `lein deps`で依存関係更新

3. **Integrantエラー**
   - `config.edn`の設定を確認
   - `#ig/ref`の参照先をチェック

## 📚 参考資料

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Duct Framework](https://github.com/ductframework/duct)
- [Integrant](https://github.com/weavejester/integrant)
- [Ataraxy](https://github.com/weavejester/ataraxy)

---

**最終更新**: 2024年12月
**バージョン**: 1.0.0 