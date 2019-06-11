package com.hyperplan.application

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

import pureconfig.generic.auto._

import com.hyperplan.domain.repositories.{
  AlgorithmsRepository,
  DomainRepository,
  PredictionsRepository,
  ProjectsRepository
}
import com.hyperplan.domain.services._
import com.hyperplan.infrastructure.logging.IOLogging
import com.hyperplan.infrastructure.metrics.PrometheusService
import com.hyperplan.infrastructure.storage.PostgresqlService
import com.hyperplan.infrastructure.streaming.{
  KafkaService,
  KinesisService,
  PubSubService
}

import scala.util.{Left, Right}

object Main extends IOApp with IOLogging {

  override def main(args: Array[String]): Unit =
    run(args.toList).runAsync(_ => IO(())).unsafeRunSync()

  import kamon.Kamon
  def killAll: IO[Unit] =
    IO.fromFuture(IO(Kamon.stopAllReporters()))

  override def run(args: List[String]): IO[ExitCode] =
    loadConfigAndStart().attempt.flatMap(
      _.fold(
        err => killAll *> logger.error(err.getMessage).as(ExitCode.Error),
        res => IO.pure(ExitCode.Success)
      )
    )

  import com.hyperplan.infrastructure.auth.JwtAuthenticationService
  def databaseConnected(
      config: ApplicationConfig
  )(implicit xa: doobie.Transactor[IO]) =
    for {
      _ <- logger.info("Connected to database")
      _ <- logger.debug("Running SQL scripts")
      _ <- PrometheusService.monitor
      //_ <- KamonSystemMonitorService.start
      _ <- PostgresqlService.initSchema
      _ <- logger.debug("SQL scripts have been runned successfully")
      projectsRepository = new ProjectsRepository
      algorithmsRepository = new AlgorithmsRepository
      predictionsRepository = new PredictionsRepository
      domainRepository = new DomainRepository
      _ = logger.info("Starting GCP Pubsub service")
      pubSubService <- if (config.gcp.pubsub.enabled) {
        logger.info("Starting GCP PubSub service") *> PubSubService(
          config.gcp.projectId,
          config.gcp.pubsub.predictionsTopicId
        ).map(Some(_))
      } else {
        IO.pure(None)
      }
      kinesisService <- KinesisService("us-east-2")
      kafkaService <- if (config.kafka.enabled) {
        KafkaService(config.kafka.topic, config.kafka.bootstrapServers)
          .map(Some(_))
      } else {
        IO.pure(None)
      }
      domainService = new DomainService(
        domainRepository
      )
      projectsService = new ProjectsService(
        projectsRepository,
        domainService
      )
      predictionsService = new PredictionsService(
        predictionsRepository,
        projectsService,
        kinesisService,
        pubSubService,
        kafkaService,
        config
      )
      algorithmsService = new AlgorithmsService(
        projectsService,
        algorithmsRepository,
        projectsRepository
      )
      privacyService = new PrivacyService(predictionsRepository)
      port = 8080
      _ <- logger.info("Services have been correctly instantiated")
      _ <- logger.info(s"Starting http server on port $port")
      publicKeyRaw = config.encryption.publicKey
      privateKeyRaw = config.encryption.privateKey
      publicKey <- JwtAuthenticationService.publicKey(publicKeyRaw)
      privateKey <- JwtAuthenticationService.privateKey(privateKeyRaw)
      _ <- logger.info("encryption keys initialized")
      _ <- {
        implicit val publicKeyImplicit = publicKey
        implicit val privateKeyImplicit = privateKey
        implicit val configImplicit = config
        Server
          .stream(
            predictionsService,
            projectsService,
            algorithmsService,
            domainService,
            privacyService,
            kafkaService,
            projectsRepository,
            port
          )
          .compile
          .drain
      }
    } yield ()

  def loadConfigAndStart() =
    pureconfig
      .loadConfig[ApplicationConfig]
      .fold(
        err => logger.error(s"Failed to load configuration because $err"),
        config => program(config)
      )

  def program(config: ApplicationConfig) =
    for {
      _ <- logger.info("Starting Foundaml server")
      _ <- logger.info("Connecting to database")
      transactor = PostgresqlService(
        config.database.postgresql.host,
        config.database.postgresql.port.toString,
        config.database.postgresql.database,
        config.database.postgresql.username,
        config.database.postgresql.password,
        config.database.postgresql.schema,
        config.database.postgresql.threadPool
      )
      _ <- transactor.use { implicit xa =>
        PostgresqlService.testConnection.attempt.flatMap {
          case Right(_) =>
            databaseConnected(config)
          case Left(err) =>
            logger.info(s"Could not connect to the database: $err")
        }
      }
    } yield ()

}