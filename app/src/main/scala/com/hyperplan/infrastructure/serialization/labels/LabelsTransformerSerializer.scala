/*
 *
 * This file is part of the Hyperplan project.
 * Copyright (C) 2019  Hyperplan
 * Authors: Antoine Sauray
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gn/u.org/licenses/>.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Hyperplan software without
 * disclosing the source code of your own applications.
 *
 *
 */

package com.hyperplan.infrastructure.serialization.labels

import com.hyperplan.domain.models.labels.transformers.TensorFlowLabelsTransformer
import io.circe.{Decoder, Encoder, HCursor, Json}

object LabelsTransformerSerializer {

  val tfLabelsTransformerEncoder: Encoder[TensorFlowLabelsTransformer] =
    (transformer: TensorFlowLabelsTransformer) =>
      Json.obj(
        ("fields", Json.fromFields(transformer.fields.toList.map {
          case (key, value) =>
            key -> Json.fromString(value)
        }))
      )

  val tfLabelsTransformerDecoder: Decoder[TensorFlowLabelsTransformer] =
    (c: HCursor) =>
      for {
        fields <- c.downField("fields").as[Map[String, String]]
      } yield TensorFlowLabelsTransformer(fields)

}
