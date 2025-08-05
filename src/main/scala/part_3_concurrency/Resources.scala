package part_3_concurrency

import utils.debugThread
import zio.*

import java.io.File
import java.util.Scanner

/**
 * Resources
 * - Finalizers: effects to be run no matter what
 * - AcquireRelease: manage acquisition + closure of resources
 * Benefits
 * - Resources are managed/closed automatically
 * - Improved code ergonomics
 * - Improved focus on business logic
 */

object Resources extends ZIOAppDefault {

  //finalizers
  def unsafeMethod(): Int = throw new RuntimeException("Not an int here for you!")
  val anAttempt = ZIO.attempt(unsafeMethod())

  //finalizers
  val attemptWithFinalizer = anAttempt.ensuring(ZIO.succeed("finalizer!").debugThread)
  // multiple finalizers
  val attemptWith2Finalizers = attemptWithFinalizer.ensuring(ZIO.succeed("another finalizer!").debugThread)
  // .onInterrupt, .onError, .onDone, onExit

  // resource lifecycle
  class Connection(url: String) {
    def open() = ZIO.succeed(s"opening connection to $url...").debugThread
    def close() = ZIO.succeed(s"closing connection to $url").debugThread
  }

  object Connection {
    def create(url: String) = ZIO.succeed(new Connection(url))
  }

  val fetchUrl = for {
    conn <- Connection.create("rockthejvm.com")
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield () // resource leak

  val correctFetchUrl = for {
    conn <- Connection.create("rockthejvm.com")
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).ensuring(conn.close()).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield () // preventing leaks

  //The above can be tedious

  /**
   acquireRelease
   - acquiring cannot be interrupted
   - all finalisers are guaranteed to run

   */
  val cleanConnection = ZIO.acquireRelease(Connection.create("rockthejvm.com"))(_.close())
  val fetchWithResource = for {
    conn <- cleanConnection
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield ()

  val fetchWithScopedResource = ZIO.scoped(fetchWithResource) // automatically close resources

  // acquireReleaseWith
  val cleanConnection_v2 = ZIO.acquireReleaseWith(
    Connection.create("rockthejvm.com") // acquire
  ) (
    _.close() // release
  ) (
    conn => conn.open() *> ZIO.sleep(300.seconds) // use
  )

  val getchWithResources_v2 = for {
    fib <- cleanConnection_v2.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield ()

  /**
   * Exercises:
   *
   */

  def run = attemptWithFinalizer

}