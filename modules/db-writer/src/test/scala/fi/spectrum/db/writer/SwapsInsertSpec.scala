package fi.spectrum.db.writer

import cats.effect.IO
import doobie.ConnectionIO
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.cardano.Order
import fi.spectrumlabs.db.writer.models.db.Swap
import fi.spectrumlabs.db.writer.repositories.OrdersRepository
import fi.spectrumlabs.db.writer.schema.SwapSchema
import io.circe.parser._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import sttp.model.Uri
import tofu.doobie.transactor.Txr
import tofu.doobie.transactor.Txr.Plain
import tofu.syntax.doobie.txr._

class SwapsInsertSpec extends AnyFlatSpec with Matchers with DbTest {

  "DB writer" should "process swap correct" in {

    val swapWithBigNumbers =
      """{"event":[{"fullTxOutAddress":{"addressCredential":{"contents":"2618e94cdb06792f05ae9b1ec78b0231f4b7f4215b1b4cf52e6342de","tag":"ScriptCredential"},"addressStakingCredential":null},"fullTxOutDatum":{"contents":{"getDatum":"d8799fd8799f4040ffd8799f581c5d16cc1a177b5d9ba9cfa9793b07e60f1fb70fea1f8aef064415d11443494147ffd8799f581cb992582b95a3ee20cb4025699808c83caaefa7bae9387b72ba2c57c34b4941475f4144415f4e4654ff1903e51a001e84801b00000001eaeef83e581ce3a0254c00994f731550f81239f12a60c9fd3ce9b9b191543152ec22d8799f581cb1bec305ddc80189dac8b628ee0adfbe5245c53b84e678ed7ec23d75ff1a83300da21b00000001eaeef83eff"},"tag":"KnownDatum"},"fullTxOutRef":{"txOutRefId":{"getTxId":"1c7c4c9802ba3a83945016b58b0506fcf872b117b9332cedaa7f2cbd43b72511"},"txOutRefIdx":2},"fullTxOutScriptRef":null,"fullTxOutValue":{"getValue":[[{"unCurrencySymbol":""},[[{"unTokenName":""},2205664514]]]]}},{"action":{"swapBase":{"unCoin":{"unAssetClass":[{"unCurrencySymbol":""},{"unTokenName":""}]}},"swapBaseIn":2200964514,"swapExFee":{"exFeePerTokenDen":8236496958,"exFeePerTokenNum":2000000},"swapMinQuoteOut":8236496958,"swapPoolId":{"unCoin":{"unAssetClass":[{"unCurrencySymbol":"b992582b95a3ee20cb4025699808c83caaefa7bae9387b72ba2c57c3"},{"unTokenName":"IAG_ADA_NFT"}]}},"swapQuote":{"unCoin":{"unAssetClass":[{"unCurrencySymbol":"5d16cc1a177b5d9ba9cfa9793b07e60f1fb70fea1f8aef064415d114"},{"unTokenName":"IAG"}]}},"swapRewardPkh":{"getPubKeyHash":"e3a0254c00994f731550f81239f12a60c9fd3ce9b9b191543152ec22"},"swapRewardSPkh":{"unStakePubKeyHash":{"getPubKeyHash":"b1bec305ddc80189dac8b628ee0adfbe5245c53b84e678ed7ec23d75"}}},"kind":"SwapK","poolId":{"unCoin":{"unAssetClass":[{"unCurrencySymbol":"b992582b95a3ee20cb4025699808c83caaefa7bae9387b72ba2c57c3"},{"unTokenName":"IAG_ADA_NFT"}]}}}],"slotNo":101822699}"""
    val defaultSwap =
      """{"event":[{"fullTxOutAddress":{"addressCredential":{"contents":"2618e94cdb06792f05ae9b1ec78b0231f4b7f4215b1b4cf52e6342de","tag":"ScriptCredential"},"addressStakingCredential":null},"fullTxOutDatum":{"contents":{"getDatum":"d8799fd8799f4040ffd8799f581c1ddcb9c9de95361565392c5bdff64767492d61a96166cb16094e54be434f5054ffd8799f581cd79bafbe9fe4b1a60e1dc777d0af754cf4e2027ec5159b8faefa14f54b4f50545f4144415f4e4654ff1903e51b000388c39502433c1b00038d7ea4c68000581c55aa458e1288691f5467638dc215385423a27ba6cddaf44240dc159fd8799f581c8c639260161c1aa71f77b79ec56f80643b9823408423ba3ef4f73aaeff1a000f42401a00170203ff"},"tag":"KnownDatum"},"fullTxOutRef":{"txOutRefId":{"getTxId":"aa5e37f0030de6d7f6bb30222be8c5aaff3ac64a8a6cf9ae663fc18ef451174c"},"txOutRefIdx":0},"fullTxOutScriptRef":null,"fullTxOutValue":{"getValue":[[{"unCurrencySymbol":""},[[{"unTokenName":""},3959390]]]]}},{"action":{"swapBase":{"unCoin":{"unAssetClass":[{"unCurrencySymbol":""},{"unTokenName":""}]}},"swapBaseIn":1000000,"swapExFee":{"exFeePerTokenDen":1000000000000000,"exFeePerTokenNum":994798530085692},"swapMinQuoteOut":1507843,"swapPoolId":{"unCoin":{"unAssetClass":[{"unCurrencySymbol":"d79bafbe9fe4b1a60e1dc777d0af754cf4e2027ec5159b8faefa14f5"},{"unTokenName":"OPT_ADA_NFT"}]}},"swapQuote":{"unCoin":{"unAssetClass":[{"unCurrencySymbol":"1ddcb9c9de95361565392c5bdff64767492d61a96166cb16094e54be"},{"unTokenName":"OPT"}]}},"swapRewardPkh":{"getPubKeyHash":"55aa458e1288691f5467638dc215385423a27ba6cddaf44240dc159f"},"swapRewardSPkh":{"unStakePubKeyHash":{"getPubKeyHash":"8c639260161c1aa71f77b79ec56f80643b9823408423ba3ef4f73aae"}}},"kind":"SwapK","poolId":{"unCoin":{"unAssetClass":[{"unCurrencySymbol":"d79bafbe9fe4b1a60e1dc777d0af754cf4e2027ec5159b8faefa14f5"},{"unTokenName":"OPT_ADA_NFT"}]}}}],"slotNo":101906397}"""

    implicit val txr: Plain[IO] = Txr.plain(xa)

    val repo = OrdersRepository.make[IO, ConnectionIO]

    val schema = new SwapSchema

    val ordersSwap1 = decode[Order](swapWithBigNumbers).toOption.get
    val ordersSwap2 = decode[Order](defaultSwap).toOption.get

    val swap1 = Swap.streamingSchema(CardanoConfig(1, Uri("0.0.0.0:9093"), 10.seconds)).apply(ordersSwap1).get
    val swap2 = Swap.streamingSchema(CardanoConfig(1, Uri("0.0.0.0:9093"), 10.seconds)).apply(ordersSwap2).get

    def run = for {
      r <- schema.insert.updateMany(List(swap1, swap2)).trans
      o1 <- repo.getOrder(ordersSwap1.fullTxOut.fullTxOutRef)
      o2 <- repo.getOrder(ordersSwap2.fullTxOut.fullTxOutRef)
    } yield {
      r shouldEqual 2
      o1.get shouldEqual swap1
      o2.get shouldEqual swap2
    }

    run.unsafeRunSync()
  }
}
