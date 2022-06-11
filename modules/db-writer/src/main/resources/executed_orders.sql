create sequence if not exists executed_deposit_seq;
create sequence if not exists executed_swap_seq;
create sequence if not exists executed_redeem_seq;

create table if not exists executed_deposit (
    id Integer not null default nextval('executed_deposit_seq'),
    pool_nft Text not null,
    coin_x Text not null,
    coin_y Text not null,
    coin_lq Text not null,
    amount_x Integer not null,
    amount_y Integer not null,
    amount_lq Integer not null,
    ex_fee Integer not null,
    reward_pkh Text not null,
    stake_pkh Text default null,
    collateral_ada Integer not null,
    order_input_id Text not null,
    user_output_id Text not null,
    pool_input_Id Text not null,
    pool_output_Id Text not null
);

create table if not exists executed_redeem (
    id Integer not null default nextval('executed_redeem_seq'),
    pool_nft Text not null,
    coin_x Text not null,
    coin_y Text not null,
    coin_lq Text not null,
    amount_x Integer not null,
    amount_y Integer not null,
    amount_lq Integer not null,
    ex_fee Integer not null,
    reward_pkh Text not null,
    stake_pkh Text default null,
    order_input_id Text not null,
    user_output_id Text not null,
    pool_input_Id Text not null,
    pool_output_Id Text not null
);

create table if not exists executed_swap (
    id Integer not null default nextval('executed_swap_seq'),
    base Text not null,
    quote Text not null,
    pool_nft Text not null,
    ex_fee_per_token_num Integer not null,
    ex_fee_per_token_den Integer not null,
    reward_pkh Text not null,
    stake_pkh Text default null,
    base_amount Integer not null,
    actual_quote: Integer not null,
    min_quote_amount Integer not null,
    order_input_id Text not null,
    user_output_id Text not null,
    pool_input_Id Text not null,
    pool_output_Id Text not null
);