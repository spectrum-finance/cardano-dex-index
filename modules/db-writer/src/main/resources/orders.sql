create table if not exists pool (
    pool_nft Text,
    pool_x Text,
    pool_y Text,
    pool_lq Text,
    pool_fee int,
    x_amount int,
    y_amount int,
    lq_amount int
);

create table if not exists deposit (
    pool_nft Text,
    deposit_x Text,
    deposit_y Text,
    deposit_lq Text,
    x_amount int,
    y_amount int,
    lq_amount int,
    ex_fee int,
    reward_pkh Text,
    stake_pkh Text,
    collateral_ada int
);

create table if not exists redeem (
    pool_nft Text,
    redeem_x Text,
    redeem_y Text,
    redeem_lq Text,
    x_amount int,
    y_amount int,
    lq_amount int,
    ex_fee int,
    reward_pkh Text,
    stake_pkh Text
);
create table if not exists swap (
    base Text,
    quote Text,
    pool_nft Text,
    fee_num int,
    ex_fee_per_token_num int,
    ex_fee_per_token_den int,
    reward_pkh Text,
    stake_pkh Text,
    base_amount int,
    min_quote_amount int
);