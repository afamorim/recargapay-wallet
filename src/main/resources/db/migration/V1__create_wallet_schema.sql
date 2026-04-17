
CREATE TABLE tb_wallet (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE TYPE transaction_type AS ENUM ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT');

CREATE TABLE tb_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES tb_wallet(id),
    type transaction_type NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    balance_after DECIMAL(19, 4) NOT NULL,
    reference_id UUID,  -- For transfers: links to the corresponding transaction in the other wallet
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_balance_after_non_negative CHECK (balance_after >= 0)
);

CREATE INDEX idx_wallet_user_id ON tb_wallet(user_id);
CREATE INDEX idx_transaction_wallet_id ON tb_transaction(wallet_id);
CREATE INDEX idx_transaction_created_at ON tb_transaction(created_at);
CREATE INDEX idx_transaction_wallet_created_at ON tb_transaction(wallet_id, created_at);
CREATE INDEX idx_transaction_reference_id ON tb_transaction(reference_id);