package com.belink.chain.listener.bubi

/**
 * Created by goldratio on 7/29/16.
 */
case class Consensus(close_time: Long, hash_set: String)

case class BlockInfo(account_hash: String,
                     consensus_value: Consensus,
                     hash: String,
                     ledger_seq: Long,
                     ledger_version: Long,
                     phash: String,
                     //total_coins: Long,
                     tx_count: Long,
                     txhash: String) {

}

case class BlockInfoRes(error_code: Int, result: BlockInfo)

case class AssetDetail(amount: Long, ext: String, length: Long, start: Long)

case class Operation(asset_amount: Option[Long],
                     asset_code: Option[String],
                     asset_issuer: Option[String],
                     asset_type: Option[Int],
                     dest_address: Option[String],
                     details: Seq[AssetDetail],
                     metadata: Option[String],
                     source_address: Option[String],
                     `type`: Int)

case class Signature(address: String, public_key: String, sign_data: String)

case class Transaction(apply_time: Long,
                       error_code: Int,
                       hash: String,
                       ledger_seq: Long,
                       metadata: String,
                       operations: Seq[Operation],
                       sequence_number: Long,
                       signatures: Seq[Signature],
                       source_address: String)

case class TransactionArray(total_count: Long, transactions: Seq[Transaction])

case class TransactionRes(error_code: Int, result: TransactionArray)

case class Asset(amount: Long, code: String, details: Seq[AssetDetail], issuer: String, `type`: Int)

case class Threshold(high_threshold: Int, low_threshold: Int, master_weight: Int, med_threshold: Int)

case class AddressInfo(address: String,
                       assets: Seq[Asset],
                       hash: String,
                       last_close_time: Long,
                       metadata: String,
                       previous_ledger_seq: Long,
                       previous_tx_hash: String,
                       signers: Seq[Signature],
                       threshold: Threshold,
                       tx_seq: Long)

case class AddressInfoRes(error_code: Int, result: Option[AddressInfo])
