CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX idx_refresh_tokens_token
    ON refresh_tokens(token);

-- id is client-generated (Android creates the UUID locally); the same id is reused
-- on the server so it can act as the sync identity. updated_at is the client's local
-- edit timestamp and drives Last Edit Wins; created_at is server-only metadata.
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_categories_user_name
        UNIQUE (user_id, name)
);

CREATE INDEX idx_categories_user_id
    ON categories(user_id);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    amount NUMERIC(15, 2) NOT NULL,
    type VARCHAR(10) NOT NULL
        CHECK (type IN ('EXPENSE', 'INCOME')),
    category_id UUID REFERENCES categories(id),
    date TIMESTAMPTZ NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_transactions_user_id
    ON transactions(user_id);

CREATE INDEX idx_transactions_user_date
    ON transactions(user_id, date);

CREATE INDEX idx_transactions_category_id
    ON transactions(category_id);
