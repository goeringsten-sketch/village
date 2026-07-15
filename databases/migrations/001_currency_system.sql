-- ============================================
-- Village Currency Database Schema
-- ============================================

-- Globale Spieler-Währung (durch PlayerCurrency Plugin gemanagt)
-- Keine Tabelle nötig, wird extern gespeichert

-- ============================================
-- Lokale Dorf-Währungen
-- ============================================

CREATE TABLE IF NOT EXISTS village_currencies (
    village_uuid VARCHAR(36) PRIMARY KEY,
    currency_name VARCHAR(100) NOT NULL,
    currency_symbol VARCHAR(10) NOT NULL,
    starting_amount DOUBLE DEFAULT 100,
    trading_enabled BOOLEAN DEFAULT FALSE,
    total_in_circulation DOUBLE DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Spieler-Guthaben in lokaler Währung
CREATE TABLE IF NOT EXISTS village_player_balances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    village_uuid VARCHAR(36) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    balance DOUBLE DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_uuid) REFERENCES village_currencies(village_uuid) ON DELETE CASCADE,
    UNIQUE(village_uuid, player_uuid)
);

-- Villager-Guthaben in lokaler Währung
CREATE TABLE IF NOT EXISTS village_villager_balances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    village_uuid VARCHAR(36) NOT NULL,
    villager_uuid VARCHAR(36) NOT NULL,
    balance DOUBLE DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_uuid) REFERENCES village_currencies(village_uuid) ON DELETE CASCADE,
    UNIQUE(village_uuid, villager_uuid)
);

-- Transaktions-History (für Audit-Trail)
CREATE TABLE IF NOT EXISTS village_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    village_uuid VARCHAR(36) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,  -- 'PLAYER_TO_PLAYER', 'PLAYER_TO_VILLAGER', 'VILLAGER_TO_PLAYER'
    from_uuid VARCHAR(36),
    to_uuid VARCHAR(36),
    amount DOUBLE NOT NULL,
    currency_type VARCHAR(10) NOT NULL,    -- 'LOCAL', 'GLOBAL'
    reason VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_uuid) REFERENCES village_currencies(village_uuid) ON DELETE CASCADE
);

-- ============================================
-- Villager-Inventare und Handel
-- ============================================

-- Villager-Lagering
CREATE TABLE IF NOT EXISTS village_villager_resources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    village_uuid VARCHAR(36) NOT NULL,
    villager_uuid VARCHAR(36) NOT NULL,
    item_id VARCHAR(50) NOT NULL,
    quantity INTEGER DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_uuid) REFERENCES village_currencies(village_uuid) ON DELETE CASCADE,
    UNIQUE(village_uuid, villager_uuid, item_id)
);

-- Villager Verkaufs-Angebote
CREATE TABLE IF NOT EXISTS village_trade_offers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    village_uuid VARCHAR(36) NOT NULL,
    villager_uuid VARCHAR(36) NOT NULL,
    item_id VARCHAR(50) NOT NULL,
    display_name VARCHAR(100),
    quantity_per_trade INTEGER DEFAULT 1,
    price DOUBLE NOT NULL,
    currency_type VARCHAR(10) NOT NULL,  -- 'LOCAL', 'GLOBAL'
    stock INTEGER DEFAULT 0,
    offer_type VARCHAR(20) NOT NULL,    -- 'SELL', 'BUY'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_uuid) REFERENCES village_currencies(village_uuid) ON DELETE CASCADE
);

-- ============================================
-- Marktplatz-Shops
-- ============================================

CREATE TABLE IF NOT EXISTS marketplace_shops (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    village_uuid VARCHAR(36) NOT NULL,
    owner_uuid VARCHAR(36) NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    owner_type VARCHAR(20) NOT NULL,  -- 'PLAYER', 'VILLAGER'
    chest_location VARCHAR(200),      -- Serialized Location
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (village_uuid) REFERENCES village_currencies(village_uuid) ON DELETE CASCADE
);

-- Marktplatz-Shelf-Einträge
CREATE TABLE IF NOT EXISTS marketplace_shelf_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL,
    item_id VARCHAR(50) NOT NULL,
    display_name VARCHAR(100),
    quantity_per_trade INTEGER DEFAULT 1,
    price DOUBLE NOT NULL,
    currency_type VARCHAR(10) NOT NULL,  -- 'LOCAL', 'GLOBAL'
    entry_type VARCHAR(20) NOT NULL,   -- 'BUY', 'SELL'
    stock INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES marketplace_shops(id) ON DELETE CASCADE
);

-- ============================================
-- Spieler-zu-Spieler Trades
-- ============================================

CREATE TABLE IF NOT EXISTS player_trade_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_uuid VARCHAR(36) NOT NULL,
    receiver_uuid VARCHAR(36) NOT NULL,
    global_currency_offer DOUBLE,
    items_wanted VARCHAR(1000),        -- JSON serialized: {"WHEAT": 8, "POTATO": 4}
    status VARCHAR(20) DEFAULT 'PENDING',  -- 'PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- ============================================
-- Indizes für Performance
-- ============================================

CREATE INDEX IF NOT EXISTS idx_village_player_balances ON village_player_balances(village_uuid, player_uuid);
CREATE INDEX IF NOT EXISTS idx_village_villager_balances ON village_villager_balances(village_uuid, villager_uuid);
CREATE INDEX IF NOT EXISTS idx_transactions_village ON village_transactions(village_uuid, created_at);
CREATE INDEX IF NOT EXISTS idx_trade_offers_villager ON village_trade_offers(village_uuid, villager_uuid);
CREATE INDEX IF NOT EXISTS idx_marketplace_shop_owner ON marketplace_shops(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_trade_requests_receiver ON player_trade_requests(receiver_uuid, status);
