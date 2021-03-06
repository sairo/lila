package lila.search

import actorApi._

import scalastic.elasticsearch.{ Indexer ⇒ EsIndexer }

import akka.actor._
import akka.pattern.pipe
import play.api.libs.json._

final class TypeIndexer(
    esIndexer: Fu[EsIndexer],
    indexName: String,
    typeName: String,
    mapping: JsObject,
    indexQuery: JsObject ⇒ Funit) extends Actor {

  def receive = {

    case Search(request) ⇒ withEs { es ⇒
      sender ! SearchResponse(request.in(indexName, typeName)(es))
    }

    case Count(request) ⇒ withEs { es ⇒
      sender ! CountResponse(request.in(indexName, typeName)(es))
    }

    case RebuildAll ⇒ {
      self ! Clear
      indexQuery(Json.obj()) pipeTo sender
    }

    case Optimize ⇒ withEs {
      _.optimize(Seq(indexName))
    }

    case InsertOne(id, doc) ⇒ withEs {
      _.index(indexName, typeName, id, Json stringify doc)
    }

    case InsertMany(list) ⇒ withEs { es ⇒
      es bulk {
        list map {
          case (id, doc) ⇒ es.index_prepare(indexName, typeName, id, Json stringify doc).request
        }
      }
    }

    case RemoveOne(id) ⇒ withEs {
      _.delete(indexName, typeName, id)
    }

    case RemoveQuery(query) ⇒ withEs {
      _.deleteByQuery(Seq(indexName), Seq(typeName), query)
    }

    case Clear ⇒ withEs { es ⇒
      try {
        es.createIndex(indexName, settings = Map())
      }
      catch {
        case e: org.elasticsearch.indices.IndexAlreadyExistsException ⇒
      }
      try {
        es.deleteByQuery(Seq(indexName), Seq(typeName))
        es.waitTillActive()
        es.deleteMapping(indexName :: Nil, typeName.some)
      }
      catch {
        case e: org.elasticsearch.indices.TypeMissingException ⇒
      }
      es.putMapping(indexName, typeName, Json stringify Json.obj(typeName -> mapping))
      es.refresh()
    }
  }

  private def withEs(f: EsIndexer ⇒ Unit) {
    esIndexer foreach f
  }
}
