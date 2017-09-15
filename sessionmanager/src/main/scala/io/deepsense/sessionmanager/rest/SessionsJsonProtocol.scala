/**
 * Copyright (c) 2016, CodiLime Inc.
 */

package io.deepsense.sessionmanager.rest

import spray.httpx.SprayJsonSupport
import spray.json._

import io.deepsense.commons.json.IdJsonProtocol
import io.deepsense.commons.rest.ClusterDetailsJsonProtocol
import io.deepsense.commons.rest.ClusterDetailsJsonProtocol._
import io.deepsense.models.json.graph.NodeStatusJsonProtocol
import io.deepsense.sessionmanager.rest.requests.CreateSession
import io.deepsense.sessionmanager.rest.responses.{ListSessionsResponse, NodeStatusesResponse}
import io.deepsense.sessionmanager.service.{Session, Status}


trait SessionsJsonProtocol
  extends DefaultJsonProtocol
  with IdJsonProtocol
  with SprayJsonSupport
  with NodeStatusJsonProtocol {

  implicit val statusFormat = new RootJsonFormat[Status.Value] {
    override def read(json: JsValue): Status.Value = json match {
      case JsString(value) =>
        value match {
          case "running" => Status.Running
          case "creating" => Status.Creating
          case "error" => Status.Error
        }
      case x => deserializationError(s"Expected 'status' to be a string but got $x")
    }

    override def write(obj: Status.Value): JsValue = JsString(obj.toString)
  }

  implicit val sessionFormat = jsonFormat3(Session.apply)

  implicit val nodeStatusesResponseFormat = jsonFormat1(NodeStatusesResponse)

  implicit val createSessionFormat = jsonFormat2(CreateSession)

  implicit val listSessionsResponseFormat = new RootJsonFormat[ListSessionsResponse] {
    override def write(obj: ListSessionsResponse): JsValue = {
      JsObject(
        "sessions" -> JsArray(obj.sessions.map(_.toJson): _*)
      )
    }

    override def read(json: JsValue): ListSessionsResponse = {
      val sessions = json.asJsObject().fields.getOrElse(
        "sessions", deserializationError("Expected 'sessions' field!"))
      sessions match {
        case JsArray(elements) => ListSessionsResponse(elements.map(_.convertTo[Session]).toList)
        case x => deserializationError(s"Expected 'sessions' to be an array but got $x")
      }
    }
  }
}

object SessionsJsonProtocol extends SessionsJsonProtocol
