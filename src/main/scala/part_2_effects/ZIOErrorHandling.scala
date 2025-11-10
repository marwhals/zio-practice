package part_2_effects

import zio.*

import java.net.NoRouteToHostException
//import scala.sys.process.processInternal.IOException
import java.io.IOException
import scala.util.{Failure, Success, Try}

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
   * Exercise
   * - Implement a version of fromTry, fromOption, fromEither, either, absolve
   * -> using fold and foldZIO
   */

  //  def try2ZIO[A](aTry: Try[A]): ZIO[Any, Throwable, A]
  def try2ZIO[A](aTry: Try[A]): Task[A] = aTry match {
    case Failure(exception) => ZIO.fail(exception)
    case Success(value) => ZIO.succeed(value)
  }

  def either2ZIO[A, B](anEither: Either[A, B]): ZIO[Any, A, B] = anEither match {
    case Left(value) => ZIO.fail(value)
    case Right(value) => ZIO.succeed(value)
  }

  def option2ZIO[A](anOption: Option[A]): ZIO[Any, Option[Nothing], A] = anOption match {
    case Some(value) => ZIO.succeed(value)
    case None => ZIO.fail(None)
  }

  def zio2zioEither[R, A, B](zio: ZIO[R, A, B]): ZIO[R, Nothing, Either[A, B]] = zio.foldZIO(
    error => ZIO.succeed(Left(error)),
    value => ZIO.succeed(Right(value))
  )

  def absolveZIO[R, A, B](zio: ZIO[R, Nothing, Either[A, B]]): ZIO[R, A, B] = zio.flatMap {
    case Left(e) => ZIO.fail(e)
    case Right(v) => ZIO.succeed(v)
  }

  /*
    Errors - Present in type signature of the ZIO (similar to "checked" exceptions)
    Defects - Not present in the type signature, unforeseen and not present in the ZIO type signature

    ZIO[R,E,A] can finish with Exit[E,A]
    - Success[A] containing A
    - Cause[E]
      - Fail[E] containing the error
      - Die(t: Throwable) which was unforeseen
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
   * Exercises
   */
  // 1 - make this effect fail with a typed error
  val aBadFailure = ZIO.succeed[Int](throw new RuntimeException("This is bad!"))
  //----
  val aBetterFailure = aBadFailure.sandbox // expose the defect in the cause
  val aBetterFailure_v2 = aBadFailure.unrefine { // surfaces out the exception in the error channel
    case e => e
  }

  // 2 - transform a zio into another zio with a narrower(same as refining) exception type
  def ioException[R, A](zio: ZIO[R, Throwable, A]): ZIO[R, IOException, A] = {
    zio.refineOrDie {
      case ioe: IOException => ioe
    }
  }

  // 3
  def left[R, E, A, B](zio: ZIO[R, E ,Either[A, B]]): ZIO[R, Either[E, A], B] = {
    zio.foldZIO(
      e => ZIO.fail(Left(e)),
      either => either match {
        case Left(a) => ZIO.fail(Right(a))
        case Right(b) => ZIO.succeed(b)
      }
    )
  }

  // 4
  val database = Map(
    "daniel" -> 123,
    "alice" -> 789
  )

  case class QueryError(reason: String)
  case class UserProfile(name: String, phone: Int)

  def lookupProfile(userId: String): ZIO[Any, QueryError, Option[UserProfile]] =
    if (userId != userId.toLowerCase())
      ZIO.fail(QueryError("user ID format is invalid"))
    else
      ZIO.succeed(database.get(userId).map(phone => UserProfile(userId, phone)))

  // surface out all the failed cases of this API
  def betterLookupProfile(userId: String):ZIO[Any, Option[QueryError], UserProfile] = {
    lookupProfile(userId).foldZIO(
      error => ZIO.fail(Some(error)),
      profileOption => profileOption match {
        case Some(profile) => ZIO.succeed(profile)
        case None => ZIO.fail(None)
      }
    )
  }

  def betterLookupProfile_v2(userId: String): ZIO[Any, Option[QueryError], UserProfile] = {
    lookupProfile(userId).some
  }

  /**
   * Error handling
   * - Attempt: wrap an expression that may throw
   * - Catch/catchAll: Process potential errors
   * - Fold/foldZIO: Process both success and failure
   * - Conversions between Try/Either/Option to ZIO
   * 
   * Errors and Defects
   * - Errors: Expected failures present in the type signature
   * - Defects: Unforeseen failures, not present in the type signature
   * How to handle
   * -> Turn failures in to defects (.orDie).
   * -> Narrow failure type, leave the rest as defects.
   * -> Treat failure causes, including defects.
   * 
   * 
   */

  override def run = ???

}
