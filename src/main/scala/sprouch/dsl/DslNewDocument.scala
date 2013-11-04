package sprouch.dsl

import sprouch._
import java.util.UUID
import scala.concurrent.Future
import spray.json.RootJsonFormat
import scala.concurrent.ExecutionContext


trait DslDocument[A] {
  
}

class DslNewDocument[A: RootJsonFormat](id: String, data: A, attachments: Map[String, AttachmentStub])(implicit val ec:ExecutionContext)
  extends NewDocument[A](id, data, attachments) with DslDocument[A] {
  
  def this(data: A)(implicit ec:ExecutionContext) = this(UUID.randomUUID.toString.toLowerCase, data, Map())

  def create(implicit db: Future[Database]): Future[RevedDocument[A]] = db.flatMap(_.createDoc(data))
  def create(id: String)(implicit db: Future[Database]): Future[RevedDocument[A]] = db.flatMap(_.createDoc(id, data))
  def createViews(implicit db: Future[Database], ev: A =:= Views): Future[RevedDocument[Views]] = {
    db.flatMap(_.createViews(this.asInstanceOf[NewDocument[Views]])) //cast will always work due to evidence parameter
  }
}

class DslNewDocSeq[A: RootJsonFormat](data: Seq[A])(implicit val ec:ExecutionContext) {
  def create(implicit db: Future[Database]): Future[Seq[RevedDocument[A]]] = {
    db.flatMap(_.bulkPut(data.map(new NewDocument(_))))
  }
}
