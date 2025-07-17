(ns todo.port.todo-repository)

;; Todo リポジトリのプロトコル
(defprotocol TodoRepository
  "Todoエンティティのデータアクセスを抽象化するプロトコル"
  
  (find-all [this]
    "すべてのTodoを取得する")
  
  (find-by-id [this id]
    "IDでTodoを取得する")
  
  (find-by-status [this completed]
    "完了状態でTodoをフィルタリングする")
  
  (save [this todo]
    "Todoを保存する（新規作成または更新）")
  
  (delete [this id]
    "IDでTodoを削除する")
  
  (count-todos [this]
    "Todoの総数を取得する")
  
  (count-completed [this]
    "完了済みTodoの数を取得する"))