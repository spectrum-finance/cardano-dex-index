package fi.spectrumlabs.markets.api.services

trait PoolsHolder[F[_]] {
  def get(tokenA: String, tokenB: String): F[BigDecimal]
}

object PoolsHolder {

  final private class Impl[F[_]] extends PoolsHolder[F] {
    def get(tokenA: String, tokenB: String): F[BigDecimal] = ???
  }
}