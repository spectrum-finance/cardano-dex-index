create sequence if not exists txn_id_seq;
create sequence if not exists output_id_seq;
create sequence if not exists input_id_seq;
create sequence if not exists redeemer_id_seq;

create table if not exists transaction (
    id Integer not null default nextval('txn_id_seq'),
    block_hash Text not null,
    block_index BIGINT not null,
    hash Text not null,
    invalid_before BIGINT default null,
    invalid_hereafter BIGINT default null,
    metadata Jsonb default null,
    size Integer not null,
    timestamp Integer not null,
    primary key (hash, block_index)
);

create table if not exists output (
    id Integer not null default nextval('output_id_seq'),
    tx_hash Text ,
    tx_index BIGINT,
    ref Text not null,
    block_hash Text not null,
    index Integer not null,
    addr Text not null,
    raw_addr Text not null,
    payment_cred Text default null,
    value Jsonb,
    data_hash Text default null,
    data Jsonb default null,
    data_bin Text default null,
    spent_by_tx_hash Text default null,
    primary key (ref, index)
);

create table if not exists input (
    id Integer not null default nextval('input_id_seq'),
    tx_hash Text not null,
    tx_index BIGINT not null,
    out_ref Text not null,
    out_index Text not null,
    redeemer_index Integer default null,
    primary key (id)
);

create table if not exists redeemer (
    id Integer not null default nextval('redeemer_id_seq'),
    tx_hash Text not null,
    tx_index BIGINT not null,
    unit_mem BIGINT not null,
    unit_step BIGINT not null,
    fee BIGINT not null,
    purpose Text not null,
    index Integer not null,
    script_hash Text not null,
    data Jsonb default null,
    data_bin Text default null,
    primary key (id)
);

create unique index if not exists tx_id on transaction (hash, block_index);

create unique index if not exists tx_out_tx_id on output (tx_hash, tx_index);
create unique index if not exists tx_out_pcred on output (payment_cred);

create unique index if not exists tx_in_tx_id on input (tx_hash, tx_index);
create unique index if not exists tx_in_redeemer on input (redeemer_index);
create unique index if not exists tx_in_out on input (out_ref, out_index);

create unique index if not exists redeemer_tx on redeemer (tx_hash, tx_index);
create unique index if not exists redeemer_id on redeemer (index);

create sequence if not exists executed_deposit_seq;
create sequence if not exists executed_swap_seq;
create sequence if not exists executed_redeem_seq;
create sequence if not exists pool_seq;

create table if not exists executed_deposit (
    id Integer not null default nextval('executed_deposit_seq'),
    pool_nft Text not null,
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
    timestamp BIGINT not null
);

create table if not exists executed_redeem (
    id Integer not null default nextval('executed_redeem_seq'),
    pool_nft Text not null,
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
    timestamp BIGINT not null
);

create table if not exists executed_swap (
    id Integer not null default nextval('executed_swap_seq'),
    base Text not null,
    quote Text not null,
    pool_nft Text not null,
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
    timestamp BIGINT not null
);

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

create unique index if not exists pool_id on transaction (id);
