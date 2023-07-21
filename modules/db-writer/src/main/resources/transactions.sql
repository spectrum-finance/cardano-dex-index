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