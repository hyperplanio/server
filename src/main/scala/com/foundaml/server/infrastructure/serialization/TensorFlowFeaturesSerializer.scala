package com.foundaml.server.infrastructure.serialization

import com.foundaml.server.domain.models.features._
import io.circe.syntax._
import io.circe.{Encoder, _}
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf
import scalaz.zio.Task
import scalaz.zio.interop.catz._

object TensorFlowFeaturesSerializer {

  import io.circe.generic.extras.semiauto._

  implicit val encodeTFClassificationFeatures
      : Encoder[TensorFlowClassificationFeatures] =
    (features: TensorFlowClassificationFeatures) =>
      Json.obj(
        ("signature_name", Json.fromString(features.signatureName)),
        ("examples", Json.arr(Json.fromFields(features.examples.flatMap {
          case TensorFlowFloatFeature(key, value) =>
            Json.fromDouble(value).map(json => key -> json)
          case TensorFlowIntFeature(key, value) =>
            Some(key -> Json.fromInt(value))
          case TensorFlowStringFeature(key, value) =>
            Some(key -> Json.fromString(value))
          case TensorFlowFloatVectorFeature(key, values) =>
            Some(key -> Json.fromValues(values.flatMap(Json.fromFloat)))
          case TensorFlowIntVectorFeature(key, values) =>
            Some(key -> Json.fromValues(values.map(Json.fromInt)))
          case TensorFlowStringVectorFeature(key, values) =>
            Some(key -> Json.fromValues(values.map(Json.fromString)))
        })))
      )

  implicit val entityEncoder
      : EntityEncoder[Task, TensorFlowClassificationFeatures] =
    jsonEncoderOf[Task, TensorFlowClassificationFeatures]

  def encodeJson(features: TensorFlowClassificationFeatures): Json =
    features.asJson

}