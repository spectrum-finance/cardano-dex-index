import scala.io.Source

val file = Source.fromFile("/Users/aleksandr/IdeaProjects/cardano-dex-index/modules/db-writer/src/main/scala/fi/spectrumlabs/db/writer/classes/test.txt").getLines.toList.filter(str => str.nonEmpty)

val my = Source.fromFile("/Users/aleksandr/IdeaProjects/cardano-dex-index/modules/db-writer/src/main/scala/fi/spectrumlabs/db/writer/classes/my.txt").getLines().toList

my.intersect(file)