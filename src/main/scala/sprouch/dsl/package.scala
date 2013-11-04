package sprouch

import spray.json.RootJsonFormat
import scala.concurrent.Future
import scala.annotation.implicitNotFound
import spray.json.JsonFormat
import spray.json.JsValue
import sprouch.StaleOption.notStale
import scala.language.implicitConversions


//TODO : Define other method to introduce global execution context!!!!!
import scala.concurrent.ExecutionContext


package object dsl {
  
  def queryView[K,V](viewDocName:String, viewName:String,
      flags:Set[ViewQueryFlag] = ViewQueryFlag.default,
      key:Option[String] = None,
      keys:List[String] = Nil,
      startKey:Option[JsValue] = None,
      endKey:Option[JsValue] = None,
      startKeyDocId:Option[String] = None,
      endKeyDocId:Option[String] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      groupLevel:Option[Int] = None,
      stale:StaleOption = notStale)(implicit db:Future[Database], jsfk:JsonFormat[K], jsfv:JsonFormat[V], ec:ExecutionContext) = {
    db.flatMap(_.queryView[K,V](viewDocName, viewName, flags, key, keys, startKey, endKey, startKeyDocId, endKeyDocId, limit, skip, groupLevel, stale))
  }
  implicit def dataToDslDoc[A:RootJsonFormat](data:A)(implicit ec:ExecutionContext):DslNewDocument[A] = {
    new DslNewDocument(data)
  }
  implicit def dataToDslNewDocSeq[A:RootJsonFormat](data:Seq[A])(implicit ec:ExecutionContext):DslNewDocSeq[A] = {
    new DslNewDocSeq(data)
  }
  implicit def dataToDslRevedDocSeq[A:RootJsonFormat](data:Seq[RevedDocument[A]])(implicit ec:ExecutionContext):DslRevedDocSeq[A] = {
    new DslRevedDocSeq(data)
  }
  
  implicit def dslDoc[A:RootJsonFormat](doc:RevedDocument[A])(implicit ec:ExecutionContext):DslRevedDocument[A] = {
    new DslRevedDocument(doc.id, doc.rev, doc.data, doc.attachments)
  }
  implicit def newToDslDoc[A:RootJsonFormat](doc:NewDocument[A])(implicit ec:ExecutionContext):DslNewDocument[A] = {
    new DslNewDocument(doc.id, doc.data, doc.attachments)
  }
  def get[A](id:String)(implicit db:Future[Database], rjf:RootJsonFormat[A], ec:ExecutionContext):Future[RevedDocument[A]] = {
    db.flatMap(_.getDoc[A](id))
  }
  def get[A](doc:RevedDocument[A])(implicit db:Future[Database], rjf:RootJsonFormat[A], ec:ExecutionContext):Future[RevedDocument[A]] = {
    db.flatMap(_.getDoc[A](doc))
  }
  class EnhancedFuture[A](f:Future[A])(implicit ec:ExecutionContext) {
    def either = {
      f.map(Right(_)).recover {
        case e:Exception => Left(e)
      }
    }
    def option = {
      f.map(Some(_)).recover {
        case e:Exception => None
      }
    }
  }
  implicit def enhanceFuture[A](f: Future[A])(implicit ec:ExecutionContext) = new EnhancedFuture(f) 
}