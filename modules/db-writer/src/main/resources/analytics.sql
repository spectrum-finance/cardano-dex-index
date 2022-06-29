create sequence if not exists executed_deposit_seq;
create sequence if not exists executed_swap_seq;
create sequence if not exists executed_redeem_seq;
create sequence if not exists pool_seq;
create sequence if not exists asset_seq;

drop table executed_deposit;
drop table executed_redeem;
drop table executed_swap;
drop table pool;

create table if not exists executed_deposit (
    id Integer not null default nextval('executed_deposit_seq'),
    pool_nft Text not null references pool (pool_id),
    coin_x Text not null,
    coin_y Text not null,
    coin_lq Text not null,
    amount_x BIGINT not null,
    amount_y BIGINT not null,
    amount_lq BIGINT not null,
    ex_fee BIGINT not null,
    reward_pkh Text not null,
    stake_pkh Text,
    collateral_ada BIGINT not null,
    order_input_id Text not null,
    user_output_id Text not null,
    pool_input_Id Text not null,
    pool_output_Id Text not null,
    timestamp BIGINT not null,
    primary key (id, pool_nft, coin_x, coin_y)
);

create unique index if not exists executed_deposit_id_index on executed_deposit (id);
create unique index if not exists executed_deposit_pool_nft_index on executed_deposit (pool_nft);

create table if not exists executed_redeem (
    id Integer not null default nextval('executed_redeem_seq'),
    pool_nft Text not null references pool (pool_id),
    coin_x Text not null,
    coin_y Text not null,
    coin_lq Text not null,
    amount_x BIGINT not null,
    amount_y BIGINT not null,
    amount_lq BIGINT not null,
    ex_fee BIGINT not null,
    reward_pkh Text not null,
    stake_pkh Text default null,
    order_input_id Text not null,
    user_output_id Text not null,
    pool_input_Id Text not null,
    pool_output_Id Text not null,
    timestamp BIGINT not null,
    primary key (id, pool_nft, coin_x, coin_y)
);

create unique index if not exists executed_redeem_id_index on executed_redeem (id);
create unique index if not exists executed_redeem_pool_nft_index on executed_redeem (pool_nft);

create table if not exists executed_swap (
    id Integer not null default nextval('executed_swap_seq'),
    base Text not null,
    quote Text not null,
    pool_nft Text not null references pool (pool_id),
    ex_fee_per_token_num BIGINT not null,
    ex_fee_per_token_den BIGINT not null,
    reward_pkh Text not null,
    stake_pkh Text default null,
    base_amount BIGINT not null,
    actual_quote BIGINT not null,
    min_quote_amount BIGINT not null,
    order_input_id Text not null,
    user_output_id Text not null,
    pool_input_Id Text not null,
    pool_output_Id Text not null,
    timestamp BIGINT not null,
    primary key (id, pool_nft, base, quote)
);

create unique index if not exists executed_swap_id_index on executed_swap (id);
create unique index if not exists executed_swap_pool_nft_index on executed_swap (pool_nft);

create table if not exists pool (
    id Integer not null default nextval('pool_seq'),
    pool_id Text not null,
    reserves_x BIGINT not null,
    reserves_y BIGINT not null,
    liquidity BIGINT not null,
    x Text not null,
    y Text not null,
    lq Text not null,
    pool_fee_num BIGINT not null,
    pool_fee_den BIGINT not null,
    out_collateral BIGINT not null,
    output_id Text not null,
    timestamp BIGINT not null
);

create table if not exists Asset (
    id Integer not null default nextval('asset_seq'),
    currency_symbol Text not null,
    token_name Text not null,
    primary key (id, currency_symbol, token_name)
);

create unique index if not exists pool_id_index on transaction (id);
create unique index if not exists pool_pool_id_index on transaction (pool_id);