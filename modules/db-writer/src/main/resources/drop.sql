delete from pool;
delete from deposit;
delete from redeem;
delete from swap;
delete from input;
delete from output;
delete from redeemer;
delete from transaction;

ALTER SEQUENCE executed_deposit_seq RESTART WITH 1;
ALTER SEQUENCE executed_swap_seq RESTART WITH 1;
ALTER SEQUENCE executed_redeem_seq RESTART WITH 1;
ALTER SEQUENCE pool_seq RESTART WITH 1;
ALTER SEQUENCE txn_id_seq RESTART WITH 1;
ALTER SEQUENCE output_id_seq RESTART WITH 1;
ALTER SEQUENCE input_id_seq RESTART WITH 1;
ALTER SEQUENCE redeemer_id_seq RESTART WITH 1;

drop table pool;
drop table deposit;
drop table redeem;
drop table swap;
drop table input;
drop table output;
drop table redeemer;
drop table transaction;