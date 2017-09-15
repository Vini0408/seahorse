/**
 * Copyright (c) 2017, CodiLime Inc.
 */

package io.deepsense.workflowmanager.migration

import java.net.URL
import java.util.UUID

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scalaz._

import akka.actor.ActorSystem
import akka.util.Timeout
import spray.json.JsValue

import io.deepsense.commons.rest.client.RestClient
import io.deepsense.commons.rest.client.datasources.DatasourceTypes._
import io.deepsense.commons.utils.{Logging, Version}
import io.deepsense.models.workflows.Workflow
import io.deepsense.workflowmanager.storage.{WorkflowRaw, WorkflowStorage}
import io.deepsense.workflowmanager.versionconverter.{DatasourcesClient, VersionConverter}

trait SeahorseDbMigration
  extends Logging {

  val datasourcemanagerUrl: URL
  val workflowStorage: WorkflowStorage
  val actorSystem: ActorSystem
  val targetVersion: Version
  val previousVersion: Version

  def migrate(): Future[Unit]

  protected case class MigrationResult(
      id: Workflow.Id,
      ownerId: UUID,
      ownerName: String,
      rawWorkflow: JsValue,
      newDatasources: DatasourceList
  )


  protected def logSuccesfullConversion(id: Any) = {
    logger.info(s"Found version ${previousVersion.humanReadable} workflow: $id - will perform " +
        s"conversion to current version ${targetVersion.humanReadable}")
  }

  protected def isConvertible(id: Any, raw: WorkflowRaw) = {
    val workflowVersion = VersionConverter.extractWorkflowVersion(raw.workflow)
    val isPreviousVersion = VersionConverter.isCompatibleWith(raw.workflow, previousVersion)
    val isTargetVersion = VersionConverter.isCompatibleWith(raw.workflow, targetVersion)


    if (!isPreviousVersion && !isTargetVersion) {
      logger.warn(s"Workflow $id in version $workflowVersion cannot be migrated to version" +
          s"${targetVersion.humanReadable}. Ignoring.")
    }

    isPreviousVersion
  }

  protected def commitMigrationsToDb(migrationResults: Seq[MigrationResult]): Seq[Future[Unit]] =
    migrationResults.map(commitMigrationToDb)

  protected def commitMigrationToDb(migrationResult: MigrationResult): Future[Unit] = {

    val MigrationResult(id, ownerId, ownerName, updatedWorkflow, newDatasources) = migrationResult

    val datasourcesClient = new DatasourcesClient(datasourcemanagerUrl, ownerId, ownerName)

    val datasourcesMigrations = for {
      newDatasource <- newDatasources
    } yield {
      datasourcesClient.insertDatasource(UUID.fromString(newDatasource.getId), newDatasource.getParams)
          .toValidationNel
    }

    import scalaz.Applicative
    import scalaz.std.list.listInstance

    val datasourceValidation = Applicative[({type L[alpha] = ValidationNel[Throwable, alpha]})#L]
        .sequence[Unit, List](datasourcesMigrations)

    datasourceValidation match {
      case scalaz.Success(_) =>
        logger.info(s"Successfully updated workflow $id to version ${targetVersion.humanReadable}, " +
            s"added ${newDatasources.size} new datasource(s)")
        workflowStorage.updateRaw(id, updatedWorkflow)

      case scalaz.Failure(nel) => Future.failed(new RuntimeException(
        s"There were errors when trying to insert datasources for workflow $id: " +
            s"${nel.map(_.getMessage).list.toList.mkString("\n")}"))
    }
  }

  private implicit def tryToValidation[R](`try`: util.Try[R]): Validation[Throwable, R] = {
    `try` match {
      case util.Success(a) => scalaz.Success(a)
      case util.Failure(t) => scalaz.Failure(t)
    }
  }
}


object SeahorseDbMigration {
  def waitForDatasourceManager(datasourcemanagerUrl: URL, actorSystem: ActorSystem): Future[Unit] = {
    DatasourceManagerPoller(actorSystem, new {
      override implicit val timeout: Timeout = 1 minute
      override implicit val as: ActorSystem = actorSystem
    } with RestClient {
      override def apiUrl: URL = datasourcemanagerUrl

      override def userName = None

      override def credentials = None

      override def userId: Option[UUID] = Some(UUID.fromString("0-0-0-0-0"))
    }).tryWork
  }

  def migrate(datasourcemanagerUrl: URL, workflowStorage: WorkflowStorage, actorSystem: ActorSystem)
      (implicit ec: ExecutionContext) = {
    val migration1_4 = Migration1_3To1_4(datasourcemanagerUrl, workflowStorage, actorSystem)
    val migration1_5 = Migration1_4To1_5(datasourcemanagerUrl, workflowStorage, actorSystem)
    for {
      _ <- migration1_4.migrate
      _ <- migration1_5.migrate
    } yield ()
  }

}
