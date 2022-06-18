package fi.spectrumlabs.markets.api.models.db

final case class Pool(poolId: String, x: String, xReserves: Long, y: String, yReserves: Long)
