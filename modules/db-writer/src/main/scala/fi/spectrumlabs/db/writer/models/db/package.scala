package fi.spectrumlabs.db.writer.models

import fi.spectrumlabs.core.models.domain.AssetClass
import fi.spectrumlabs.db.writer.models.cardano.{FullTxOutRef, AssetClass => CardanoAssetClass}
import fi.spectrumlabs.db.writer.models.orders.{TxOutRef, TxOutRefId}

package object db {

  def castFromCardano(cardanoAssetClass: CardanoAssetClass): AssetClass =
    AssetClass(
      cardanoAssetClass.unCurrencySymbol.unCurrencySymbol,
      cardanoAssetClass.unTokenName.unTokenName
    )

  def castFromCardano(cardanoTxOutRef: FullTxOutRef): TxOutRef =
    TxOutRef(
      cardanoTxOutRef.txOutRefIdx,
      TxOutRefId(cardanoTxOutRef.txOutRefId.getTxId)
    )
}
