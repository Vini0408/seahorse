/**
 * Copyright (c) 2015, CodiLime Inc.
 *
 * Owner: Witold Jedrzejewski
 */

package io.deepsense.deeplang.parameters

import spray.json._

import io.deepsense.deeplang.parameters.ValidatorType.ValidatorType

/** Represents anything that validates parameter. */
trait Validator[ParameterType] {
  val validatorType: ValidatorType

  def validate(parameter: ParameterType)

  final def toJson: JsObject = {
    import DefaultJsonProtocol._
    JsObject(
      "type" -> validatorType.toString.toJson,
      "configuration" -> configurationToJson)
  }

  protected def configurationToJson: JsObject
}