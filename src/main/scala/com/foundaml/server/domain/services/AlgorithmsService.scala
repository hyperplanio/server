package com.foundaml.server.domain.services

import scalaz.zio.{Task, ZIO}
import com.foundaml.server.domain.factories.ProjectFactory
import com.foundaml.server.domain.models._
import com.foundaml.server.domain.models.backends.{
  Backend,
  LocalClassification,
  TensorFlowClassificationBackend,
  TensorFlowRegressionBackend
}
import com.foundaml.server.domain.models.errors.{
  IncompatibleAlgorithm,
  InvalidArgument
}
import com.foundaml.server.domain.models.features.transformers.TensorFlowFeaturesTransformer
import com.foundaml.server.domain.models.labels.transformers.TensorFlowLabelsTransformer
import com.foundaml.server.domain.repositories.{
  AlgorithmsRepository,
  ProjectsRepository
}
import com.foundaml.server.infrastructure.logging.IOLogging

class AlgorithmsService(
    algorithmsRepository: AlgorithmsRepository,
    projectsRepository: ProjectsRepository,
    projectFactory: ProjectFactory
) extends IOLogging {

  def validateEqualSize(
      expectedSize: Int,
      actualSize: Int,
      featureName: String
  ) =
    if (expectedSize != actualSize) {
      Some(s"The $featureName size is incorrect for the project")
    } else {
      None
    }

  def validateClassificationAlgorithm(
      algorithm: Algorithm,
      project: ClassificationProject
  ) = {
    algorithm.backend match {
      case LocalClassification(computed) => Nil
      case TensorFlowClassificationBackend(
          _,
          _,
          TensorFlowFeaturesTransformer(signatureName, fields),
          TensorFlowLabelsTransformer(labels)
          ) =>
        val size = project.configuration.features match {
          case FeaturesConfiguration(
              featuresClasses: List[FeatureConfiguration]
              ) =>
            featuresClasses.size
        }
        List(
          validateEqualSize(
            size,
            fields.size,
            "features"
          ),
          validateEqualSize(
            project.configuration.labels.size,
            labels.size,
            "labels"
          )
        ).flatten
      case TensorFlowRegressionBackend(_, _, _) =>
        List(IncompatibleAlgorithm(algorithm.id))
    }

  }

  def validateRegressionAlgorithm(
      algorithm: Algorithm,
      project: RegressionProject
  ) = {
    algorithm.backend match {
      case LocalClassification(computed) => Nil
      case TensorFlowRegressionBackend(
          _,
          _,
          TensorFlowFeaturesTransformer(signatureName, fields)
          ) =>
        val size = project.configuration.features match {
          case FeaturesConfiguration(
              featuresClasses: List[FeatureConfiguration]
              ) =>
            featuresClasses.size
        }
        List(
          validateEqualSize(
            size,
            fields.size,
            "features"
          )
        ).flatten
      case TensorFlowClassificationBackend(_, _, _, _) =>
        List(IncompatibleAlgorithm(algorithm.id))
    }
  }

  def createAlgorithm(id: String, backend: Backend, projectId: String) = {
    for {
      project <- projectFactory.get(projectId)
      algorithm = Algorithm(
        id,
        backend,
        projectId
      )
      errors = project match {
        case classificationProject: ClassificationProject =>
          validateClassificationAlgorithm(algorithm, classificationProject)
        case regressionProject: RegressionProject =>
          validateRegressionAlgorithm(algorithm, regressionProject)
      }
      _ <- if (errors.isEmpty) {
        Task(Unit)
      } else {
        val message = s"The following errors occurred: ${errors.mkString(", ")}"
        warnLog(message) *> Task.fail(
          InvalidArgument(message)
        )
      }
      insertResult <- algorithmsRepository.insert(algorithm)
      result <- insertResult.fold(
        err => {
          warnLog(
            s"An error occurred while inserting an algorithm: ${err.getMessage}"
          ) *>
            Task.fail(err)
        },
        _ => Task.succeed(algorithm)
      )
    } yield result
  }
}