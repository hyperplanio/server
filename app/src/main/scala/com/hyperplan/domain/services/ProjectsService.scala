package com.hyperplan.domain.services

import com.hyperplan.domain.repositories.DomainRepository
import com.foundaml.server.controllers.requests.PostProjectRequest
import com.hyperplan.domain.models._
import com.hyperplan.domain.errors._
import com.hyperplan.domain.models.features._
import com.hyperplan.domain.repositories.ProjectsRepository
import com.hyperplan.infrastructure.logging.IOLogging
import doobie.util.invariant.UnexpectedEnd

import cats.effect.IO
import cats.implicits._

import scalacache.Cache
import scalacache.CatsEffect.modes._

class ProjectsService(
    projectsRepository: ProjectsRepository,
    domainService: DomainService,
    cache: Cache[Project]
) extends IOLogging {

  val regex = "[0-9a-zA-Z-_]*"
  def validateAlphaNumerical(input: String): List[ProjectError] = {
    if (input.matches(regex)) {
      Nil
    } else {
      List(
        InvalidProjectIdentifier(
          s"$input is not an alphanumerical id. It should satisfy the following regular expression: $regex"
        )
      )
    }
  }

  def createEmptyProject(
      projectRequest: PostProjectRequest
  ): IO[Project] = {

    val streamConfiguration =
      projectRequest.topic.map(topic => StreamConfiguration(topic))
    val featuresIO = domainService.readFeatures(projectRequest.featuresId)
    ((projectRequest.problem, projectRequest.labelsId) match {
      case (Classification, Some(labelsId)) =>
        val labelsIO = domainService.readLabels(labelsId)
        (featuresIO, labelsIO).mapN {
          case (Some(features), Some(labels)) =>
            ClassificationProject(
              projectRequest.id,
              projectRequest.name,
              ClassificationConfiguration(
                features,
                labels,
                streamConfiguration
              ),
              Nil,
              NoAlgorithm()
            )
          case (None, None) =>
            ???
          case (None, Some(_)) =>
            ???
          case (Some(_), None) =>
            ???
        }
      case (Regression, None) =>
        featuresIO.map {
          case Some(features) =>
            RegressionProject(
              projectRequest.id,
              projectRequest.name,
              RegressionConfiguration(
                features,
                streamConfiguration
              ),
              Nil,
              NoAlgorithm()
            )
          case None =>
            ???
        }
      case (Classification, None) =>
        IO.raiseError(
          ClassificationProjectRequiresLabels(
            "A classification project requires labels"
          )
        )
      case (Regression, Some(_)) =>
        IO.raiseError(
          RegressionProjectDoesNotRequireLabels(
            "A regression project does not require labels"
          )
        )

    }).flatMap { project =>
      for {
        insertResult <- projectsRepository.insert(project)
        result <- insertResult.fold(
          err => logger.warn(err.getMessage) *> IO.raiseError(err),
          _ => IO.pure(project)
        )
      } yield result

    }
  }.flatMap { project =>
    cache.remove[IO](project.id).map(_ => project)
  }

  def updateProject(
      projectId: String,
      name: Option[String],
      policy: Option[AlgorithmPolicy]
  ): IO[Int] =
    projectsRepository
      .transact(
        projectsRepository
          .read(projectId)
          .map {
            case project: ClassificationProject =>
              project.copy(
                name = name.getOrElse(project.name),
                policy = policy.getOrElse(project.policy)
              )
            case project: RegressionProject =>
              project.copy(
                name = name.getOrElse(project.name),
                policy = policy.getOrElse(project.policy)
              )
          }
          .flatMap { project =>
            projectsRepository.update(project)
          }
      )
      .flatMap { count =>
        cache.remove[IO](projectId).map(_ => count)
      }

  def readProjects =
    projectsRepository.transact(projectsRepository.readAll)

  def readProject(id: String): IO[Project] =
    cache.get[IO](id).flatMap { cacheElement =>
      cacheElement.fold(
        projectsRepository.transact(projectsRepository.read(id))
      )(
        project => IO.pure(project)
      )
    }

}