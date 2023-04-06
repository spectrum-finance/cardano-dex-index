package fi.spectrumlabs.db.writer.schema

import fi.spectrumlabs.db.writer.models.db.Deposit

class DepositSchema extends Schema[Deposit] {
  val tableName: String = "deposit"

  val fields: List[String] = List(
    "pool_nft",
    "coin_x",
    "coin_y",
    "coin_lq",
    "amount_x",
    "amount_y",
    "amount_lq",
    "ex_fee",
    "reward_pkh",
    "stake_pkh",
    "collateral_ada",
    "order_input_id",
    "user_output_id",
    "pool_input_Id",
    "pool_output_Id",
    "redeem_output_id",
    "creation_timestamp",
    "execution_timestamp",
    "order_status"
  )
}
