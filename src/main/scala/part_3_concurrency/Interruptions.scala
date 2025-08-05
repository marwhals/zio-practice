package part_3_concurrency

import utils.debugThread
import zio.*

object Interruptions extends ZIOAppDefault {

  val zioWithTime =
    (
      ZIO.succeed("starting computation").debugThread *>
        ZIO.sleep(2.seconds) *>
        ZIO.succeed(42).debugThread
      ).onInterrupt(ZIO.succeed("I was interrupted!").debugThread)
  // onInterrupt, onDone

  val interruption = for {
    fib <- zioWithTime.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("Interrupting!").debugThread *> fib.interrupt // <---- is an effect, blocks the calling fiber until the interrupted fiber is done/or is interrupted
    _ <- ZIO.succeed("Interruption successful").debugThread
    result <- fib.join
  } yield result

  val interruption_v2 = for {
    fib <- zioWithTime.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("Interrupting!").debugThread *> fib.interruptFork
    _ <- ZIO.succeed("Interruption successful").debugThread
    result <- fib.join
  } yield result

  /*
    Automatic interruption
   */
  // outliving a parent fiber
  val parentEffect =
    ZIO.succeed("spawning fiber").debugThread *>
      // zioWithTime.fork *> // child fiber
      zioWithTime.forkDaemon *> // this fiber will now be a child of the main fiber
      ZIO.sleep(1.second) *>
      ZIO.succeed("parent successful").debugThread // done here

  val testOutlivingParent = for {
    parentEffectFib <- parentEffect.fork
    _ <- ZIO.sleep(3.seconds)
    _ <- parentEffectFib.join
  } yield ()
    // child fibers will be (automatically) interrupted if the parent fiber is completed

    //racing
    val slowEffect = (ZIO.sleep(2.seconds) *> ZIO.succeed("slow").debugThread).onInterrupt(ZIO.succeed("[slow] interrupted").debugThread)
    val fastEffect = (ZIO.sleep(1.seconds) *> ZIO.succeed("fast").debugThread).onInterrupt(ZIO.succeed("[fast] interrupted").debugThread)
    val aRace = slowEffect.race(fastEffect)
    val testRace = aRace.fork *> ZIO.sleep(3.seconds)

  /**
   * TODO - Exercises - 17:10
   */

  def run = interruption

  /**
   * Interruptions
   * - Interrupting a fiber
   * --- is an effect
   * --- (semantically) blocks until interruption is completed
   * --- Can also be forked!
   * - Can run effects when interrupted
   * --- Resource cleanup
   * - Child fibers
   * --- New fiber is a child of the fiber that forked it
   * --- Child fiber cannot outlive a parent, will be interrupted
   * --- Can spawn fibers as children of "main", via .forkDaemon
   * - Race
   * --- Two effects are run on separate fibers
   * --- Winners (first to finish) dictates the result, loser is interrupted
   */

}
