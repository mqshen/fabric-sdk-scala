/**
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements.  See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License.  You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package belink.server.checkpoints

import java.io._
import java.util.regex.Pattern

import belink.server.epoch.EpochEntry
import com.ynet.belink.common.Topic

import scala.collection._

object OffsetCheckpointFile {
  private val WhiteSpacesPattern = Pattern.compile("\\s+")
  private[checkpoints] val CurrentVersion = 0

  object Formatter extends CheckpointFileFormatter[(Topic, Long)] {
    override def toLine(entry: (Topic, Long)): String = {
      s"${entry._1.topic} ${entry._2}"
    }

    override def fromLine(line: String): Option[(Topic, Long)] = {
      WhiteSpacesPattern.split(line) match {
        case Array(topic, offset) =>
          Some(new Topic(topic), offset.toLong)
        case _ => None
      }
    }
  }
}

trait OffsetCheckpoint {
  def write(epochs: Seq[EpochEntry])
  def read(): Seq[EpochEntry]
}

/**
  * This class persists a map of (Partition => Offsets) to a file (for a certain replica)
  */
class OffsetCheckpointFile(val f: File) {
  val checkpoint = new CheckpointFile[(Topic, Long)](f, OffsetCheckpointFile.CurrentVersion,
    OffsetCheckpointFile.Formatter)

  def write(offsets: Map[Topic, Long]): Unit = checkpoint.write(offsets.toSeq)

  def read(): Map[Topic, Long] = checkpoint.read().toMap

}
