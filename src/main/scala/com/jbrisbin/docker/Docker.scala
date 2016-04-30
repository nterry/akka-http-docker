package com.jbrisbin.docker

import java.net.URI
import java.time.Instant

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, ResponseEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.jackson.Json4sScalaModule
import org.json4s.{DefaultFormats, Formats, Serialization, jackson}

import scala.concurrent.Future

/**
  * @author Jon Brisbin <jbrisbin@basho.com>
  */
object Docker {

  implicit val system = ActorSystem("docker")
  implicit val materializer = ActorMaterializer()

  implicit val formats: Formats = DefaultFormats
  implicit val serialization: Serialization = jackson.Serialization

  import system.dispatcher

  val dockerHost: URI = URI.create(System.getenv().getOrDefault("DOCKER_HOST", "https://127.0.0.1:2376"))

  lazy val sslctx = ConnectionContext.https(SSL.createSSLContext)
  lazy val docker = Http().outgoingConnectionHttps(dockerHost.getHost, dockerHost.getPort, sslctx)

  val mapper = new ObjectMapper()
  mapper.registerModule(new DefaultScalaModule())

  def containers(containers: Containers = Containers()): Future[List[Container]] = {
    var params = Map[String, String]()

    containers.all match {
      case true => params += ("all" -> "true")
      case _ =>
    }
    containers.limit match {
      case l if l > 0 => params += ("limit" -> l.toString)
      case _ =>
    }
    containers.before match {
      case null =>
      case b => params += ("before" -> b)
    }
    containers.since match {
      case null =>
      case s => params += ("since" -> s)
    }
    containers.size match {
      case true => params += ("size" -> "true")
      case _ =>
    }
    containers.filters match {
      case m if m.isEmpty =>
      case m => params += ("filters" -> mapper.writeValueAsString(m))
    }

    val req = RequestBuilding.Get(Uri(
      path = Uri.Path("/containers/json"),
      queryString = Some(Uri.Query(params).toString())
    ))
    request(req).flatMap(e => e.to[List[Map[String, AnyRef]]].map(l => l.map(m => {
      Container(
        id = m("Id").asInstanceOf[String],
        names = m.getOrElse("Names", Seq.empty).asInstanceOf[Seq[String]],
        image = m("Image").asInstanceOf[String],
        command = m("Command").asInstanceOf[String],
        created = Instant.ofEpochSecond(m.getOrElse("Created", 0).asInstanceOf[BigInt].toLong),
        status = m("Status").asInstanceOf[String],
        ports = m.getOrElse("Ports", Seq.empty).asInstanceOf[List[Map[String, AnyRef]]]
      )
    })))
  }

  def run(run: Run): ActorRef = {
    null
  }

  private def request(req: HttpRequest): Future[Unmarshal[ResponseEntity]] = {
    Source.single(req)
      .via(docker)
      .runWith(Sink.head)
      .map(resp => Unmarshal(resp.entity))
  }

  implicit private def convertPorts(l: List[Map[String, AnyRef]]): Seq[Port] = {
    l.map(m => {
      Port(`private` = m("PrivatePort").asInstanceOf[BigInt].toInt,
        `public` = m("PublicPort").asInstanceOf[BigInt].toInt,
        `type` = m("Type").asInstanceOf[String])
    })
  }
}
