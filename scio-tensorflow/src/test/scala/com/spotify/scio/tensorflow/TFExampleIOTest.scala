/*
 * Copyright 2019 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.tensorflow

import com.google.protobuf.ByteString
import com.spotify.scio.io.{ClosedTap, FileNamePolicySpec}
import com.spotify.scio.testing._
import com.spotify.scio.util.FilenamePolicySupplier
import com.spotify.scio.values.SCollection
import magnolify.tensorflow._
import org.tensorflow.proto.example.Example

object TFExampleIOTest {
  case class Record(i: Int, s: String)

  implicit val efInt: ExampleField.Primitive[Int] = ExampleField.from[Long](_.toInt)(_.toLong)
  implicit val efString: ExampleField.Primitive[String] =
    ExampleField.from[ByteString](_.toStringUtf8)(ByteString.copyFromUtf8)
  val recordT: ExampleType[Record] = ExampleType[Record]
}

class TFExampleIOTest extends ScioIOSpec {
  import TFExampleIOTest._

  "TFExampleIO" should "work" in {
    val xs = (1 to 100).map(x => recordT(Record(x, x.toString)))
    testTap(xs)(_.saveAsTfRecordFile(_))(".tfrecords")
    testJobTest(xs)(TFExampleIO(_))(_.tfRecordExampleFile(_))(_.saveAsTfRecordFile(_))
  }
}

class TFExampleIOFileNamePolicyTest extends FileNamePolicySpec[Example] {
  import TFExampleIOTest._

  val extension: String = ".tfrecords"
  def save(
    filenamePolicySupplier: FilenamePolicySupplier = null
  )(in: SCollection[Int], tmpDir: String, isBounded: Boolean): ClosedTap[Example] = {
    in.map(x => recordT(Record(x, x.toString)))
      .saveAsTfRecordFile(
        tmpDir,
        // TODO there is an exception with auto-sharding that fails for unbounded streams due to a GBK so numShards must be specified
        numShards = if (isBounded) 0 else TestNumShards,
        filenamePolicySupplier = filenamePolicySupplier
      )
  }

  override def failSaves = Seq(
    _.map(x => recordT(Record(x, x.toString))).saveAsTfRecordFile(
      "nonsense",
      shardNameTemplate = "NNN-of-NNN",
      filenamePolicySupplier = testFilenamePolicySupplier
    )
  )
}
