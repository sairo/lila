package lila.round

import actorApi._
import lila.game.{ Game, GameRepo, PovRef, Pov }
import lila.game.tube.gameTube
import lila.socket.actorApi.Forward
import lila.db.api._

import akka.actor.ActorRef

final class Meddler(finisher: Finisher, socketHub: ActorRef) {

  def forceAbort(id: String) {
    $find.byId(id) foreach {
      _.fold(logwarn("Cannot abort missing game " + id)) { game ⇒
        finisher forceAbort game effectFold (
          e ⇒ logwarn(e.getMessage),
          events ⇒ socketHub ! Forward(game.id, events)
        )
      }
    }
  }

  def resign(pov: Pov) {
    finisher resign pov effectFold (
      e ⇒ logwarn(e.getMessage),
      events ⇒ socketHub ! Forward(pov.game.id, events)
    )
  }

  def resign(povRef: PovRef) {
    GameRepo pov povRef foreach {
      _.fold(logwarn("Cannot resign missing game " + povRef))(resign)
    }
  }

  def finishAbandoned(game: Game) {
    game.abandoned.fold(
      finisher.resign(Pov(game, game.player)) onFailure {
        case e ⇒ logwarn("Finish abandoned game %s : ".format(game.id, e.getMessage))
      },
      logwarn("Game is not abandoned")
    )
  }
}
