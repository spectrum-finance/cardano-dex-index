package fi.spectrumlabs.db.writer.errors

sealed trait AppError {
  val error: String
}

object AppError {
  case object HandleBatchError extends AppError {
    val error: String = s"Failed to process"
  }
}
