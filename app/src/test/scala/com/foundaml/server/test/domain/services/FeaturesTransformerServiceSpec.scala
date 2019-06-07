package com.foundaml.server.test.domain.services

import org.scalatest._
import org.scalatest.Inside.inside
import com.foundaml.server.domain.models.features._
import com.foundaml.server.domain.models.features.transformers.TensorFlowFeaturesTransformer
import com.foundaml.server.infrastructure.serialization.features.FeaturesSerializer
import com.foundaml.server.infrastructure.serialization.tensorflow.TensorFlowFeaturesSerializer
import io.circe._
import io.circe.syntax._
import io.circe.parser._

class FeaturesTransformerServiceSpec extends FlatSpec {

  it should "transform features to a tensorflow classify compatible format" in {
    val features = List(
      StringFeature("test instance"),
      IntFeature(1),
      FloatFeature(0.5f)
    )

    val transformer = TensorFlowFeaturesTransformer(
      "my_signature_name",
      Set(
        "test",
        "toto",
        "titi"
      )
    )

    val transformedFeatures = transformer.transform(
      features
    )
    val expectedJson = parse(
      """
        {
          "signature_name": "my_signature_name",
          "examples": [
            { 
              "test": "test instance",
              "toto": 1,
              "titi": 0.5
            }
          ]
        }
        """
    ).getOrElse(Json.Null)

    inside(transformedFeatures) {
      case Right(tfFeatures) =>
        assert(
          Json.eqJson
            .eqv(
              TensorFlowFeaturesSerializer.encodeJson(tfFeatures),
              expectedJson
            )
        )
    }
  }
}
