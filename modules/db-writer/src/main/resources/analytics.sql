create sequence if not exists executed_deposit_seq;
create sequence if not exists executed_swap_seq;
create sequence if not exists executed_redeem_seq;
create sequence if not exists pool_seq;

drop table executed_deposit;
drop table executed_redeem;
drop table executed_swap;

create table if not exists deposit (
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
    user_output_id Text,
    pool_input_Id Text,
    pool_output_Id Text,
    redeem_output_Id Text,
    creation_timestamp BIGINT,
    execution_timestamp BIGINT,
    order_status TEXT
);

create table if not exists redeem (
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
    user_output_id Text,
    pool_input_Id Text,
    pool_output_Id Text,
    redeem_output_Id Text,
    creation_timestamp BIGINT,
    execution_timestamp BIGINT,
    order_status TEXT
);

create table if not exists swap (
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
    user_output_id Text,
    pool_input_Id Text,
    pool_output_Id Text,
    redeem_output_Id Text,
    creation_timestamp BIGINT,
    execution_timestamp BIGINT,
    order_status TEXT
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
create unique index if not exists pool_id_pool_id on pool (pool_id);