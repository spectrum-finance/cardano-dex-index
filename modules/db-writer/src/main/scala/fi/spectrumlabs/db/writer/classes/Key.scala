package fi.spectrumlabs.db.writer.classes

trait Key[T] {
  def getKey(in: T): String
}
