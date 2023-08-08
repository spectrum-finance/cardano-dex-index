package fi.spectrumlabs.db.writer.schema

import fi.spectrumlabs.db.writer.models.db.Redeem

class RedeemSchema extends Schema[Redeem] {
  val tableName: String = "redeem"

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
    "order_input_id",
    "user_output_id",
    "pool_input_Id",
    "pool_output_Id",
    "redeem_output_Id",
    "creation_timestamp",
    "execution_timestamp",
    "order_status",
    "refundable_fee"
  )
}
