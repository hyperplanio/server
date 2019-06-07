package com.foundaml.server.domain.services
import com.foundaml.server.domain.models.features.Features._
import com.foundaml.server.domain.models.ProjectConfiguration
import com.foundaml.server.domain.models.ClassificationConfiguration
import com.foundaml.server.domain.models.RegressionConfiguration
import com.foundaml.server.domain.models.FeaturesConfiguration

import com.foundaml.server.domain.models.features._

import cats.effect.IO
import cats.implicits._

import io.circe.Json
import io.circe.ACursor

object FeaturesParserService {
  def parseFeatures(configuration: ProjectConfiguration, hcursor: Json)(
      implicit domainService: DomainService
  ): IO[Features] = configuration match {
    case ClassificationConfiguration(featuresConfiguration, _) =>
      parseFeatures(
        featuresConfiguration,
        hcursor.hcursor.downField("features")
      )
    case RegressionConfiguration(featuresConfiguration) =>
      parseFeatures(
        featuresConfiguration,
        hcursor.hcursor.downField("features")
      )
  }

  def parseFeatures(configuration: FeaturesConfiguration, hcursor: ACursor)(
      implicit domainService: DomainService
  ): IO[Features] = {
    configuration.data
      .map { featuresConfiguration =>
        val jsonField = hcursor.downField(featuresConfiguration.name)
        (featuresConfiguration.featuresType, featuresConfiguration.dimension) match {
          case (FloatFeatureType, One) =>
            IO.fromEither(jsonField.as[Float])
              .map[Features](value => List(FloatFeature(value)))
          case (FloatFeatureType, Vector) =>
            IO.fromEither(jsonField.as[List[Float]])
              .map[Features](value => List(FloatVectorFeature(value)))
          case (FloatFeatureType, Matrix) =>
            IO.fromEither(jsonField.as[List[List[Float]]])
              .map[Features](value => List(FloatVector2dFeature(value)))
          case (IntFeatureType, One) =>
            IO.fromEither(jsonField.as[Int])
              .map[Features](value => List(IntFeature(value)))
          case (IntFeatureType, Vector) =>
            IO.fromEither(jsonField.as[List[Int]])
              .map[Features](value => List(IntVectorFeature(value)))
          case (IntFeatureType, Matrix) =>
            IO.fromEither(jsonField.as[List[List[Int]]])
              .map[Features](value => List(IntVector2dFeature(value)))
          case (StringFeatureType, One) =>
            IO.fromEither(jsonField.as[String])
              .map[Features](value => List(StringFeature(value)))
          case (StringFeatureType, Vector) =>
            IO.fromEither(jsonField.as[List[String]])
              .map[Features](value => List(StringVectorFeature(value)))
          case (StringFeatureType, Matrix) =>
            IO.fromEither(jsonField.as[List[List[String]]])
              .map[Features](value => List(StringVector2dFeature(value)))
          case (ReferenceFeatureType(reference), One) =>
            domainService.readFeatures(reference).flatMap { config =>
              FeaturesParserService.parseFeatures(config, jsonField)
            }
          case (reference, dimension) =>
            IO.raiseError(
              new Exception(
                s"The reference $reference features cannot be of dimension $dimension"
              )
            )
        }
      }
      .sequence
      .map(_.flatten)
  }
}
