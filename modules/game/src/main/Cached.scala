package lila.game

import lila.db.api.$count
import tube.gameTube

import scala.concurrent.duration._

import spray.caching.{ LruCache, Cache }
import play.api.libs.json.JsObject

private[game] final class Cached(ttl: Duration) {

  def nbGames: Fu[Int] = count(_.all)
  def nbMates: Fu[Int] = count(_.mate)
  def nbPopular: Fu[Int] = count(_.popular)
  def nbImported: Fu[Int] = count(_.imported)

  private def count(selector: Query.type ⇒ JsObject) =
    selector(Query) |> { sel ⇒ cache.fromFuture(sel)($count(sel)) }

  private val cache: Cache[Int] = LruCache(timeToLive = ttl)
}
