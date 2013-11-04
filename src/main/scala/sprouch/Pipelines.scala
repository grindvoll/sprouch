package sprouch

import akka.actor._
import scala.concurrent.Future
import spray.client.pipelining._
import spray.http._
import HttpMethods._
import spray.httpx.encoding.{Gzip, Deflate}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling.FromResponseUnmarshaller
import spray.json._
import spray.util._
import java.util.UUID
import akka.event.Logging
import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging
import sprouch.JsonProtocol.ErrorResponseBody
import sprouch.JsonProtocol.ErrorResponse
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps


/**
 * Configuration data, default values should be valid for a default install of CouchDB.
 * 
 * @constructor
 * 
 * @param userPass Optional pair of username and password.
 * @param https Whether to use https. If true, the config property spray.can.client.ssl-encryption
 *  must be set to on, which is the default setting in the reference.conf of this library.
 */
case class Config(
    actorSystem:ActorSystem,
    hostName:String = "localhost",
    port:Int = 5984,
    userPass:Option[(String,String)] = Option(("admin", "password")),
    https:Boolean = false,
    timeout:Timeout = (10 seconds)
)

private[sprouch] class Pipelines(config:Config) {
  import config._
  
  private val hostConnectorSetup = new Http.HostConnectorSetup(hostName, port) 
  
  // Use actor system's dispatcher for Futures
  import actorSystem.dispatcher
  
  implicit val system = actorSystem  // From config object
  implicit val to = timeout          // From config object
  
  private var httpActor:Option[ActorRef] = None
  
  private lazy val log = Logging(actorSystem, httpActor.get)
  
  private val logRequest: HttpRequest => HttpRequest = r => {
    log.info(r.toString + "\n")
    r
  }
  
  private val logResponse: HttpResponse => HttpResponse = r => {
    log.info(r.toString + "\n")
    r
  }

  /**
   * Creates new pipeline without options
   */
  def pipeline[A:FromResponseUnmarshaller]: HttpRequest => Future[A] = pipeline[A](None)
  
  /**
   * Create pipeline with options
   */
  private def pipelineInternal[A:FromResponseUnmarshaller](etag:Option[String], httpTransport:ActorRef): HttpRequest => Future[A] = {

    def unmarshalEither[A:FromResponseUnmarshaller]: HttpResponse => A = {
      hr => (hr match {
        case HttpResponse(status, _, _, _) if status.intValue == 304 => {//not modified
          throw new SprouchException(ErrorResponse(status.intValue, None))
        }
        case HttpResponse(status, _, _, _) if status.isSuccess => {          
          unmarshal[A](implicitly[FromResponseUnmarshaller[A]])(hr)
        }
        case HttpResponse(errorStatus, _, _, _) => {
          log.error(hr.toString)
          val ue = implicitly[FromResponseUnmarshaller[ErrorResponseBody]]
          val body = unmarshal[ErrorResponseBody](ue)(hr.copy(status = StatusCodes.OK))
          throw new SprouchException(ErrorResponse(errorStatus.intValue, Option(body)))
        }
      })
    }
    addHeader("accept", "application/json") ~>
    (etag match {
      case Some(etag) => addHeader("If-None-Match", "\"" + etag + "\"") 
      case None => (x:HttpRequest) => x
    }) ~>
    (userPass match {
      case Some((u,p)) => addCredentials(BasicHttpCredentials(u, p))
      case None => (x:HttpRequest) => x
    }) ~>
//  logRequest ~>
    sendReceive( httpTransport ) ~>
//  logResponse ~>
    unmarshalEither[A]
  }
  
  // Actor ref is not ready; create a future and return that
  private def pipelineInternalWait[A:FromResponseUnmarshaller](etag:Option[String]): HttpRequest => Future[A] = {
    
	// Return function that wraps it in a future. We create a new future and chain that to the other one.
	// Fetch reference to actor that manages communication towards a specific host

    // Kick off future immediately. We will wait for an answer to the fonnctor info.
	val future = (IO(Http) ? hostConnectorSetup).mapTo[Http.HostConnectorInfo]
	
	hr => {
	  // Ask for connector info
	  val res = future.flatMap[A] { f => 
	    // Store the host connector in httpActor 
	    httpActor = Option(f.hostConnector)
	    pipelineInternal(etag, f.hostConnector).apply(hr)
	  }
	  res
	}
  }
  
  // Return pipeline. If the HTTP actor is not found, we need to contact it first. 
  def pipeline[A:FromResponseUnmarshaller](etag:Option[String]): HttpRequest => Future[A] = {
    httpActor match {
      case Some(actor) => pipelineInternal[A](etag, actor) 
      case _ => pipelineInternalWait[A](etag)
    }
  }
  
}
