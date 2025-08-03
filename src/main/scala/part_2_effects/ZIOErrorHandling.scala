package part_2_effects

import zio.*

import java.net.NoRouteToHostException
import scala.sys.process.processInternal.IOException
import scala.util.Try

object ZIOErrorHandling extends ZIOAppDefault {

  // ZIOs can fail
  val aFailedZIO = ZIO.fail("Something went wrong")
  val failedWithThrowable = ZIO.fail(new RuntimeException("Boom!"))
  val failedWithDescription = failedWithThrowable.mapError(_.getMessage)

  // attempt: run an effect that might throw an exception
  val badZIO = ZIO.succeed {
    println("Trying something")
    val string: String = null
    string.length
  } // this is bad

  // use attempt if you're even unsure whether your code might throw an exception
  val anAttempt = ZIO.attempt {
    println("Trying something")
    val string: String = null
    string.length
  }

  // effect-fully catch errors
  val catchError = anAttempt.catchAll(e => ZIO.succeed(s"Returning a different value because $e"))
  //  val catchError = anAttempt.catchAll(e => ZIO.attempt(s"Returning a different value because $e"))

  val catchSelectiveErrors = anAttempt.catchSome {
    case e: RuntimeException => ZIO.succeed(s"Ignoring runtime exceptions: $e")
    case _ => ZIO.succeed("Ignoring everything else")
  }

  // chain effects
  val aBetterAttempt = anAttempt.orElse(ZIO.succeed(56))
  // fold: handle both success and failure
  val handleBoth: ZIO[Any, Nothing, String] = anAttempt.fold(ex => s"Something bad happened: $ex", value => s"Length of the string was $value")
  // effectful fold: foldZIO
  val handleBoth_v2 = anAttempt.foldZIO(
    ex => ZIO.succeed(s"Something bad happened: $ex"),
    value => ZIO.succeed(s"Length of the string was $value")
  )

  /*
   * - Conversions between Option / Try / Either to ZIO
   */

  val aTryToZIO: ZIO[Any, Throwable, Int] = ZIO.fromTry(Try(42 / 0)) // can fail with Throwable

  // either -> ZIO
  val anEither: Either[Int, String] = Right("Success!")
  val anEitherToZIO: ZIO[Any, Int, String] = ZIO.fromEither(anEither)

  // ZIO -> ZIO with Either as the value channel
  val eitherZIO = anAttempt.either

  // reverse
  val anAttempt_v2 = eitherZIO.absolve

  // option -> ZIO
  val anOption: ZIO[Any, Option[Nothing], Int] = ZIO.fromOption(Some(42))

  /**
   * TODO - Exercise
   * - Implement a version of fromTry, fromOption, fromEither, either, absolve
   * - Using fold and foldZIO
   */

  /*
    Errors - Present in type signature of the ZIO (similar to "checked" exceptions)
    Defects - Not present in the type signature, unforseen and not present in the ZIO type signature

    ZIO[R,E,A] can finish with Exit[E,A]
    - Success[A] containing A
    - Cause[E]
      - Fail[E] containing the error
      - Die(t: Throwable) which was unforseen
  */
  val divisionByZero: UIO[Int] = ZIO.succeed(1 / 0)
  val failedInt: ZIO[Any, String, Int] = ZIO.fail("I failed!")
  val failureCauseExposed: ZIO[Any, Cause[String], Int] = failedInt.sandbox
  val failureCauseHidden: ZIO[Any, String, Int] = failureCauseExposed.unsandbox
  // fold with cause
  val foldedWithCause = failedInt.foldCause(
    cause => s"this failed with ${cause.defects}",
    value => s"this succeeded with $value"
  )
  val foldedWithCause_v2 = failedInt.foldCauseZIO(
    cause => ZIO.succeed(s"this failed with ${cause.defects}"),
    value => ZIO.succeed(s"this succeeded with $value")
  )

  /*
    Good practice:
    - at a lower level, your "errors" should be treated
    - at a higher level, you should hide "errors" and assume they are unrecoverable
   */

  def callHTTPEndpoint(url: String): ZIO[Any, IOException, String] = {

    ZIO.fail(new IOException("no internet, dummy!"))
  }

  val endpointCallWithDefects: ZIO[Any, Nothing, String] =
    callHTTPEndpoint("rockthjvm.com").orDie // all errors are now defects

  // refining the error channel
  def callHTTPEndpointWideError(url: String): ZIO[Any, Exception, String] =
    ZIO.fail(new IOException("No internet!!"))

  def callHTTPEndpoint_v2(url: String): ZIO[Any, IOException, String] =
    callHTTPEndpointWideError(url).refineOrDie[IOException] {
      case _: NoRouteToHostException => new IOException(s"No route to host to $url, can't fetch page")
      case e: IOException => e
    }

  // reverse: turn defects into the error channel
  val endpointCallWithError = endpointCallWithDefects.unrefine {
    case e => e.getMessage
  }

  /*
    Combine effects with different errors
   */

  trait AppError
  case class IndexError(message: String) extends AppError
  case class DbError(message: String) extends AppError

  val callApi: ZIO[Any, IndexError, String] = ZIO.succeed("page: <html></html>")
  val queryDb: ZIO[Any, DbError, Int] = ZIO.succeed(1)
  // combine in one application
//  val combined = for {
  val combined: ZIO[Any, IndexError | DbError, (String, Int)] = for { //Scala 3 ONLY
    page <- callApi
    rowsAffected <- queryDb
  } yield (page, rowsAffected) // lost type safety -- error is the lowest common ancestor - a product...
/*
Solutions:
  - design an error model - can be overcome by defining a trait
  - use Scala 3 union types
  - .mapError to some common error type
 */

  /**
   * TODO Exercises --- 19:28
   */

  // surface out all the failed cases of this API

  override def run = ???

}
