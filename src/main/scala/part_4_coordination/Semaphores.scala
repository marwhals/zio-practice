package part_4_coordination

import zio._
import utils.*

object Semaphores extends ZIOAppDefault {

  // n permits
  // acquire, acquireN - can potentially (semantically)
  // release, releaseN
  val aSemaphore = Semaphore.make(10)

  // example: limiting the number of concurrent sessions on a server
  def doWorkWhileLoggedIn(): UIO[Int] =
    ZIO.sleep(1.second) *> Random.nextIntBounded(100)

  def login(id: Int, sem: Semaphore): UIO[Int] =
    ZIO.succeed(s"[task $id] waiting to log in").debugThread *>
      sem.withPermit {
        for {
          // critical section start
          _ <- ZIO.succeed(s"[task $id] logged in, working......").debugThread
          res <- doWorkWhileLoggedIn()
          _ <- ZIO.succeed(s"[task $id] done: $res").debugThread
        } yield res
      }

  def demoSemaphore() = for {
    sem <- Semaphore.make(2) // Semaphore.make(1) == a Mutex
    f1 <- login(1, sem).fork
    f2 <- login(2, sem).fork
    f3 <- login(3, sem).fork
    _ <- f1.join
    _ <- f2.join
    _ <- f3.join
  } yield ()

  def loginWeighted(n: Int, sem: Semaphore): UIO[Int] =
    ZIO.succeed(s"[task $n] waiting to log in with $n permits").debugThread *>
      sem.withPermits(n) { // acquire + zio + release
        for {
          // critical section starts when you acquired all n permits
          _ <- ZIO.succeed(s"[task $n] logged in, working...").debugThread
          res <- doWorkWhileLoggedIn()
          _ <- ZIO.succeed(s"[task $n] done: $res").debugThread
        } yield res
      }

  /**
   * Exercise - TODO - 10:50
   *
   */

  def run = demoSemaphore()

}
