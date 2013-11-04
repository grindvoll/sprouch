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
    https:Boolean = false
)

private[sprouch] class Pipelines(config:Config) {
  import config._
  
  private val hostConnectorSetup = new Http.HostConnectorSetup(hostName, port) 

  // TODO: Don't hard code the timeout; make it a member of config class
  implicit val timeout:akka.util.Timeout = new akka.util.Timeout(10000)  
  
  // Use actor system's dispatcher for Futures
  import actorSystem.dispatcher
  
  implicit val system = actorSystem
  
  private val httpTransport = {
    
    // TODO: Replace this with correct TCP handling in akka.io 
    
    /*
    val ioBridge = IOExtension(actorSystem).ioBridge()
    val httpClient = actorSystem.actorOf(Props(new HttpClient(ioBridge)))
    actorSystem.actorOf(Props(new HttpConduit(httpClient, hostName, port, https)))
    */

    // Fetch reference to actor managing a host...
    println("Sending connector setup message...")
    val future = (IO(Http) ? hostConnectorSetup).mapTo[Http.HostConnectorInfo]
    println("Waiting for result....")
    val res = Await.result(future, 10 seconds)
    println("Got actor reference!!!!")
    res.hostConnector
  }
  
  private val log = Logging(actorSystem, httpTransport)
  
  private val logRequest: HttpRequest => HttpRequest = r => {
    log.info(r.toString + "\n")
    r
  }
  
  private val logResponse: HttpResponse => HttpResponse = r => {
    log.info(r.toString + "\n")
    r
  }  
  def pipeline[A:FromResponseUnmarshaller]: HttpRequest => Future[A] = pipeline[A](None)
  
  def pipeline[A:FromResponseUnmarshaller](etag:Option[String]): HttpRequest => Future[A] = {
    
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
      case Some((u,p)) => { println(s"Adding credentials>>>>>>>>> ${u}:${p}");  addCredentials(BasicHttpCredentials(u, p)) }
      case None => (x:HttpRequest) => x
    }) ~>
//  logRequest ~>
    sendReceive(httpTransport) ~>
//  logResponse ~>
    unmarshalEither[A]
  }
  
}
