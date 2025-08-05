package part_3_concurrency

import utils.debugThread
import zio.*

object Parallelism extends ZIOAppDefault {

  val meaningOfLife = ZIO.succeed(42)
  val favLang = ZIO.succeed("Scala")
  val combined = meaningOfLife.zip(favLang) // combines / zips in a sequential way

  // combine in parallel
  val combinedPar = meaningOfLife.zipPar(favLang) // combination is in paralell

  def myZipPar[R, E, A, B](zioa: ZIO[R, E, A], ziob: ZIO[R, E, B]): ZIO[R, E, (A, B)] = {
    val exits = for {
      fiba <- zioa.fork
      fibb <- ziob.fork
      exita <- fiba.await
      exitb <- exita match {
        case Exit.Success(value) => fibb.await
        case Exit.Failure(_) => fibb.interrupt
      }
    } yield (exita, exitb)

    exits.flatMap {
      case (Exit.Success(a), Exit.Success(b)) => ZIO.succeed((a, b)) // happy path
      case (Exit.Success(_), Exit.Failure(cause)) => ZIO.failCause(cause) // one of them failed
      case (Exit.Failure(cause), Exit.Success(_)) => ZIO.failCause(cause) // one of them failed
      case (Exit.Failure(c1), Exit.Failure(c2)) => ZIO.failCause(c1 && c2)
    }
  }

  // paralell combinators
  // zipPar, zipWithPar

  // collectAllPar
  val effects: Seq[ZIO[Any, Nothing, Int]] = (1 to 10).map(i => ZIO.succeed(i).debugThread)
  val collectedValues: ZIO[Any, Nothing, Seq[Int]] = ZIO.collectAllPar(effects) // "traverse"

  // forEachPar
  val printlnParallel = ZIO.foreachPar((1 to 10).toList)(i => ZIO.succeed(println(i)))

  // reduceAllPar, mergeAllPar
  val sumPar = ZIO.reduceAllPar(ZIO.succeed(0), effects)(_ + _)
  val sumPar_v2 = ZIO.mergeAllPar(effects)(0)(_ + _)
  
  /*
  - If all the effects succeed, all good
  - One effect fails => everyone else is interrupted, error is surfaces
  - One effect is interrupted => everyone else is interrupted, error = interruption (for the big expression)
  - If the entire thing is interrupted => all effects are interrupted
   */

  /**
   * Excercise: TODO 21:06
   * 
   */

  def run = collectedValues.debugThread

  /**
   * Parallelism
   * - ZIO has parallel combinators that will automatically
   * ---- Run effects on separate fibers
   * ---- Check for errors and interruptions
   * ---- Interrupt the complete effects
   * 
   * 
   */

}
