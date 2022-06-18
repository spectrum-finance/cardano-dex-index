package fi.spectrumlabs.rates.resolver.repositories

trait RatesRepo[F[_]] {
  def put(key: String, value: String): F[Unit]
}
