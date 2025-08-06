package part_3_concurrency

import utils.*
import zio.*

import java.util.concurrent.Executors

/**
 * Asynchronous Effects
 * - Use a callback object created by the ZIO runtime
 * - Invoke the callback with a value/error to complete the ZIO
 * - Block the ZIO (semantically) until the callback is invoked
 * ZIO Native Implementations
 * - ZIO.fromFuture
 * - ZIO.never
 */

object AsynchronousEffects extends ZIOAppDefault {

  // CALLBACK-based
  // asynchronous
  object LoginService {
    case class AuthError(message: String)

    case class UserProfile(email: String, name: String)

    // thread pool
    val executor = Executors.newFixedThreadPool(8)

    // "database"
    val passwd = Map(
      "123@damail.com" -> "RockTheJVM1!"
    )

    // the profile data
    val database = Map(
      "123@damail.com" -> "Daniel"
    )

    def login(email: String, password: String)(onSuccess: UserProfile => Unit, onFailure: AuthError => Unit) =
      executor.execute { () =>
        println(s"[${Thread.currentThread().getName}] Attempting login for $email")
        passwd.get(email) match {
          case Some(p) if p == password => onSuccess(UserProfile(email, database(email))) // equivalent
          case Some(`password`) => onSuccess(UserProfile(email, database(email))) // backtick trick
          case Some(_) => onFailure(AuthError("Incorrect password."))
          case None => onFailure(AuthError(s"User $email doesn't exist. Please sign up."))
        }
      }

  }

  def loginAsZIO(id: String, pw: String): ZIO[Any, LoginService.AuthError, LoginService.UserProfile] =
    ZIO.async[Any, LoginService.AuthError, LoginService.UserProfile] { cb => // callback object created by ZIO
      LoginService.login(id, pw)(
        profile => cb(ZIO.succeed(profile)), // notify the ZIO fiber to complete the ZIO with a success
        error => cb(ZIO.fail(error)) // same, with a failure
      )
    }

  val loginProgram = for {
    email <- Console.readLine("Email: ")
    pass <- Console.readLine("Password: ")
    profile <- loginAsZIO(email, pass).debugThread
    _ <- Console.printLine(s"Welcome to the blah, ${profile.name}")
  } yield ()

  /**
   * TODO - Exercises - 13:27
   *
   */


  def run = loginProgram

}
