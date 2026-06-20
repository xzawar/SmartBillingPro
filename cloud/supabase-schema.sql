-- =====================================================================
--  Smart Billing Pro - Supabase (PostgreSQL) schema
--  Run this ONCE in the Supabase SQL Editor (Dashboard -> SQL -> New query).
--  It mirrors the desktop app's tables and adds the columns needed for
--  two-way sync (a global id, an updated_at timestamp and a soft-delete flag),
--  plus the store "enabled" and owner "activation" flags the admin controls.
-- =====================================================================

-- Every synced row uses a globally-unique text id (a UUID the desktop app
-- generates) so two devices never collide. local_id is the row's SQLite id on
-- the device that created it (handy for debugging). updated_at + deleted drive
-- last-write-wins sync.

-- ---------- STORES ----------
create table if not exists stores (
  id            text primary key,            -- global uuid
  local_id      integer,
  name          text not null,
  phone         text,
  address       text,
  invoice_counter integer not null default 1000,
  enabled       boolean not null default true,   -- admin can disable the whole store
  activated     boolean not null default false,  -- admin activates a newly-registered store
  owner_pending boolean not null default true,   -- true until the admin activates the owner
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

-- ---------- USERS (staff accounts that log into the app AND the web) ----------
create table if not exists users (
  id            text primary key,            -- global uuid
  local_id      integer,
  store_id      text references stores(id),  -- null/0-store for the admin
  username      text not null unique,
  password_hash text not null,               -- SHA-256 hex (same as the desktop app)
  full_name     text,
  role          text not null,               -- ADMIN | OWNER | MANAGER | CASHIER
  active        boolean not null default true,
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

-- ---------- SUPPLIERS ----------
create table if not exists suppliers (
  id          text primary key,
  local_id    integer,
  store_id    text references stores(id),
  name        text not null,
  phone       text,
  address     text,
  notes       text,
  updated_at  timestamptz not null default now(),
  deleted     boolean not null default false
);

-- ---------- PRODUCTS ----------
create table if not exists products (
  id           text primary key,
  local_id     integer,
  store_id     text references stores(id),
  sku          text not null,
  name         text not null,
  category     text,
  cost_price   text not null default '0',
  sale_price   text not null default '0',
  quantity     integer not null default 0,
  supplier_id  integer,
  updated_at   timestamptz not null default now(),
  deleted      boolean not null default false
);

-- ---------- CUSTOMERS ----------
create table if not exists customers (
  id             text primary key,
  local_id       integer,
  store_id       text references stores(id),
  name           text not null,
  phone          text,
  address        text,
  loyalty_points integer not null default 0,
  updated_at     timestamptz not null default now(),
  deleted        boolean not null default false
);

-- ---------- SETTINGS (one row per store) ----------
create table if not exists settings (
  id                  text primary key,      -- global uuid (one per store)
  store_id            text references stores(id) unique,
  business_name       text,
  address             text,
  phone               text,
  invoice_prefix      text,
  default_tax_percent text,
  currency_symbol     text,
  footer_message      text,
  low_stock_threshold integer,
  return_policy_days   integer,
  loyalty_points_per_100 integer,
  updated_at          timestamptz not null default now(),
  deleted             boolean not null default false
);

-- ---------- INVOICES ----------
create table if not exists invoices (
  id              text primary key,
  local_id        integer,
  store_id        text references stores(id),
  invoice_number  text not null,
  customer_id     integer,
  customer_name   text,
  customer_phone  text,
  customer_address text,
  subtotal        text,
  discount        text,
  tax_percent     text,
  tax_amount      text,
  grand_total     text,
  amount_paid     text,
  change_due      text,
  payment_method  text,
  status          text,
  cashier         text,
  created_at      text,
  void_reason     text,
  loyalty_earned  integer not null default 0,
  updated_at      timestamptz not null default now(),
  deleted         boolean not null default false
);

-- ---------- INVOICE ITEMS (child rows reference the invoice's global id) ----------
create table if not exists invoice_items (
  id            text primary key,
  local_id      integer,
  store_id      text references stores(id),
  invoice_uuid  text references invoices(id),
  product_id    integer,
  sku           text,
  name          text,
  unit_price    text,
  cost_price    text,
  quantity      integer,
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

-- ---------- PURCHASE ORDERS ----------
create table if not exists purchase_orders (
  id            text primary key,
  local_id      integer,
  store_id      text references stores(id),
  reference     text not null,
  supplier_id   integer,
  supplier_name text,
  date          text,
  status        text,
  total         text,
  updated_at    timestamptz not null default now(),
  deleted       boolean not null default false
);

-- ---------- PURCHASE ORDER ITEMS ----------
create table if not exists purchase_order_items (
  id           text primary key,
  local_id     integer,
  store_id     text references stores(id),
  po_uuid      text references purchase_orders(id),
  product_id   integer,
  product_name text,
  quantity     integer,
  cost_price   text,
  updated_at   timestamptz not null default now(),
  deleted      boolean not null default false
);

-- ---------- AUDIT LOG ----------
create table if not exists audit_log (
  id          text primary key,
  local_id    integer,
  store_id    text references stores(id),
  timestamp   text,
  username    text,
  action      text,
  details     text,
  updated_at  timestamptz not null default now(),
  deleted     boolean not null default false
);

-- ---------- Helpful indexes for fast "changed since" pulls ----------
create index if not exists idx_products_store_updated   on products(store_id, updated_at);
create index if not exists idx_customers_store_updated  on customers(store_id, updated_at);
create index if not exists idx_invoices_store_updated   on invoices(store_id, updated_at);
create index if not exists idx_items_store_updated      on invoice_items(store_id, updated_at);
create index if not exists idx_suppliers_store_updated  on suppliers(store_id, updated_at);
create index if not exists idx_po_store_updated         on purchase_orders(store_id, updated_at);
create index if not exists idx_poi_store_updated        on purchase_order_items(store_id, updated_at);
create index if not exists idx_audit_store_updated      on audit_log(store_id, updated_at);
create index if not exists idx_users_store_updated      on users(store_id, updated_at);

-- ---------- Auto-bump updated_at on every change ----------
create or replace function set_updated_at() returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

do $$
declare t text;
begin
  foreach t in array array['stores','users','suppliers','products','customers',
      'settings','invoices','invoice_items','purchase_orders',
      'purchase_order_items','audit_log']
  loop
    execute format('drop trigger if exists trg_%1$s_updated on %1$s', t);
    execute format('create trigger trg_%1$s_updated before update on %1$s
        for each row execute function set_updated_at()', t);
  end loop;
end $$;

-- ---------- Seed the administrator (username: admin / password: admin123) ----------
-- SHA-256 hex of "admin123" = the same hash the desktop app stores.
insert into users (id, local_id, store_id, username, password_hash, full_name, role, active)
values (
  gen_random_uuid()::text, 1, null, 'admin',
  '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
  'System Administrator', 'ADMIN', true
)
on conflict (username) do nothing;

-- =====================================================================
--  ROW LEVEL SECURITY
--  All access goes through the Vercel API using the SERVICE ROLE key,
--  which bypasses RLS. We still enable RLS with NO public policies so the
--  anon/public key cannot read or write anything directly. Never put the
--  service-role key in the desktop app or the website front-end.
-- =====================================================================
do $$
declare t text;
begin
  foreach t in array array['stores','users','suppliers','products','customers',
      'settings','invoices','invoice_items','purchase_orders',
      'purchase_order_items','audit_log']
  loop
    execute format('alter table %s enable row level security', t);
  end loop;
end $$;
