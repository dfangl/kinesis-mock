package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.{Blocker, IO}
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class StartStreamEncryptionTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should start stream encryption")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      Blocker[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context)
            .rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(50.millis))
          keyId <- IO(keyIdGen.one)
          _ <- cache
            .startStreamEncryption(
              StartStreamEncryptionRequest(
                EncryptionType.KMS,
                keyId,
                streamName
              ),
              context
            )
            .rethrow
          describeReq = DescribeStreamSummaryRequest(streamName)
          checkStream1 <- cache
            .describeStreamSummary(describeReq, context)
            .rethrow
          _ <- IO.sleep(
            cacheConfig.startStreamEncryptionDuration.plus(50.millis)
          )
          checkStream2 <- cache
            .describeStreamSummary(describeReq, context)
            .rethrow
        } yield assert(
          checkStream1.streamDescriptionSummary.encryptionType.contains(
            EncryptionType.KMS
          ) &&
            checkStream1.streamDescriptionSummary.keyId.contains(keyId) &&
            checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
            checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE
        )
      )
  })
}