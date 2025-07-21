CREATE TABLE todos (
  id SERIAL PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- インデックスを作成（パフォーマンス向上）
CREATE INDEX idx_todos_completed ON todos(completed);
CREATE INDEX idx_todos_created_at ON todos(created_at);

-- サンプルデータを挿入
INSERT INTO todos (title, description, completed, created_at, updated_at) VALUES
('Clojureを学習する', '関数型プログラミングの基本を理解する', FALSE, '2024-01-01 10:00:00+00', '2024-01-01 10:00:00+00'),
('TODOアプリを作成する', 'クリーンアーキテクチャで実装する', FALSE, '2024-01-02 09:00:00+00', '2024-01-02 09:00:00+00'),
('ドキュメントを読む', 'Ductフレームワークのドキュメントを確認', TRUE, '2024-01-01 08:00:00+00', '2024-01-01 15:00:00+00'); 