package com.foundaml.server.utils

import com.foundaml.server.domain.models._
import com.foundaml.server.domain.models.labels._
import com.foundaml.server.domain.models.backends._

import java.util.UUID

object ProjectGenerator {

  val computed = Labels(
    Set(
      labels.ClassificationLabel(
        "class1",
        0.1f
      ),
      labels.ClassificationLabel(
        "class2",
        0.2f
      ),
      labels.ClassificationLabel(
        "class3",
        0.3f
      )
    )
  )

  val projectId = UUID.randomUUID().toString
  val defaultAlgorithm = Algorithm(
    "algorithm id",
    Local(computed),
    projectId
  )

  def withLocalBackend() = Project(
    projectId,
    "example project",
    Classification(),
    "tf.cl",
    "tf.cl",
    Map.empty,
    DefaultAlgorithm(defaultAlgorithm)
  )
}
