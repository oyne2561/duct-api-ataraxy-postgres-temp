-- インデックスを削除
DROP INDEX IF EXISTS idx_todos_created_at;
DROP INDEX IF EXISTS idx_todos_completed;

-- テーブルを削除
DROP TABLE IF EXISTS todos; 