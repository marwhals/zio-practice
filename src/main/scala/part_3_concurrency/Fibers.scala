package part_3_concurrency

import zio.{Exit, Fiber, ZIO, ZIOAppDefault}
import utils.*

/**
 * Paralellism vs Concurrency refresherrrrrrrrrrrr
 * - Two computations are parallel if they run at the same time
 * - Two computations are concurrent if their lifecycles overlap
 *
 * - Parallel computations may no be concurrent
 * ---- Independent tasks
 * - Concurrent computaitons may not be paralell
 * ---- Multi tasking on the same CPU with resource sharing
 *
 */


object Fibers extends ZIOAppDefault {

  val meaningOfLife = ZIO.succeed(42)
  val favLang = ZIO.succeed("Scala")

  // Fiber = lightweight thread - descriptions of a computation that runs on ZIO/xeo(XD) runtime
  def createFiber: Fiber[Throwable, String] = ??? //Impossible to create manually

  def sameThreadIO = for {
    mol <- meaningOfLife.debugThread
    lang <- favLang.debugThread
  } yield (mol, lang)
  
  val differentThreadIO = for {
    _ <- meaningOfLife.debugThread.fork
    _ <- favLang.debugThread.fork
  } yield ()
  
  val meaningOfLifeFiber: ZIO[Any, Nothing, Fiber[Throwable, Int]] = meaningOfLife.fork
  
  // join a fiber
  def runOnAnotherThread[R,E,A](zio: ZIO[R,E,A]) = for {
    fib <- zio.fork
    result <- fib.join
  } yield result
  
  // awaiting a fiber
  def runOnAnotherThread_v2[R,E,A](zio: ZIO[R,E,A]) = for {
    fib <- zio.fork
    result <- fib.await
  } yield result match {
    case Exit.Success(value) => s"succeeded with $value"
    case Exit.Failure(cause) => s"failed with $cause"
  }
  
  // poll - peek at the result of the fiber right now without blocking
  val peekFiber = for {
    fib <- ZIO.attempt {
      Thread.sleep(1000)
      42
    }.fork
    result <- fib.poll
  } yield result
  
  // compose fibers
  // zip
  val zippedFibers = for {
    fib1 <- ZIO.succeed("Result from fiber 1").debugThread.fork
    fib2 <- ZIO.succeed("Result from fiber 2").debugThread.fork
    fiber = fib1.zip(fib2)
    tuple <- fiber.join
  } yield tuple
  
  // or else
  val chainedFibers = for {
    fiber1 <- ZIO.fail("not good!").debugThread.fork
    fiber2 <- ZIO.succeed("Rock the JVM!").debugThread.fork
    fiber = fiber1.orElse(fiber2)
    message <- fiber.join
  } yield message

  def run = chainedFibers.debugThread
//  def run = runOnAnotherThread(meaningOfLife).debugThread
}
