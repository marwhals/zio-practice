package part_3_concurrency

import utils.*
import zio.*

/**
 * Interruptions
 * - ZIOs can be marked as uninterruptible
 * - Define specific interruptible regions by using the restorer
 * --- Everything wrapped in the restore call has the same flag as before
 * --- Everything else if not interruptible
 * - The restorer is the local opposite of uninterruptible
 * --- Can be called as many times as we like
 */

object MasteringInterruptions extends ZIOAppDefault {

  // interruptions:
  // fib.interrupt
  // ZIO.race, ZIO.zipPar, ZIO.collectAllPar
  // outliving parent fiber

  // manual interruption
  val aManuallyInterruptedZIO = ZIO.succeed("computing....").debugThread *> ZIO.interrupt *> ZIO.succeed(42).debugThread

  // finalizer
  val effectWithInterruptionFinalizer = aManuallyInterruptedZIO.onInterrupt(ZIO.succeed("I was interrupted!").debugThread)

  // uninterruptible
  // payment flow to NOT be interrupted
  //  val fussyPaymentSystem = for {
  //    _ <- ZIO.succeed("payment running, don't cancel me.....").debugThread
  //    _ <- ZIO.sleep(1.second) // the actual payment
  //    _ <- ZIO.succeed("payment completed").debugThread
  //  } yield ()

  val fussyPaymentSystem = (
    ZIO.succeed("payment running, don't cancel me...").debugThread *>
      ZIO.sleep(1.second) *> // the actual payment
      ZIO.succeed("payment completed").debugThread
    ).onInterrupt(ZIO.succeed("MEGA CANCEL OF DOOM!").debugThread) // don't want this triggered

  val cancellationOfDoom = for {
    fib <- fussyPaymentSystem.fork
    _ <- ZIO.sleep(500.millis) *> fib.interrupt
    _ <- fib.join
  } yield ()

  // ZIO.uninterruptible
  val atomicPayment = ZIO.uninterruptible(fussyPaymentSystem) // make a ZIO atomic
  val atomicPayment_v2 = fussyPaymentSystem.uninterruptible // same - this is atomic
  val noCancellationOfDoom = for {
    fib <- atomicPayment.fork
    _ <- ZIO.sleep(500.millis) *> fib.interrupt
    _ <- fib.join
  } yield ()

  // interruptibility is regional
  val zio1 = ZIO.succeed(1)
  val zio2 = ZIO.succeed(2)
  val zio3 = ZIO.succeed(3)
  val zioComposed = (zio1 *> zio2 *> zio3).uninterruptible // ALL the zios are uninterruptible
  val zioComposed2 = (zio1 *> zio2.interruptible *> zio3).uninterruptible // inner scopes override outer scopes

  // uninterruptibleMask
  /*
  example: an authentication service
  - input password, can be interrupted, because otherwise it might block the fiber indefinitely
  - verify password, which cannot be interrupted once it's triggered
   */

  val inputPassword = for {
    _ <- ZIO.succeed("Input password").debugThread
    _ <- ZIO.succeed("(typing password)").debugThread
    _ <- ZIO.sleep(2.seconds)
    pass <- ZIO.succeed("RockTheJVM1!")
  } yield pass

  def verifyPassword(pw: String) = for {
    _ <- ZIO.succeed("verifying...").debugThread
    _ <- ZIO.sleep(2.seconds)
    result <- ZIO.succeed(pw == "RockTheJVM!")
  } yield result

  val authFlow = ZIO.uninterruptibleMask { restore =>
    // everything is uninterruptible except what is wrapped in restore
    for {
      pw <- restore(inputPassword).onInterrupt(ZIO.succeed("Authentication timed out. Try again later").debugThread)
      // ^^ restores the interruptibility flag of this ZIO at the time of the call
      verification <- verifyPassword(pw)
      _ <- if (verification) ZIO.succeed("Authentication successful.").debugThread
      else ZIO.succeed("Authentication failed.").debugThread
    } yield ()
  }

  val authProgram = for {
    authFib <- authFlow.fork
    _ <- ZIO.sleep(3.seconds) *> ZIO.succeed("Attempting to cancel authentication.....").debugThread *> authFib.interrupt
    _ <- authFib.join
  } yield ()

  /**
   * TODO Exercises
   *
   */

  def run = ???

  /**
   * Uninterruptable calls are masks which suppress cancellation. Restorer opens "gaps" in the uninterruptible region.
   * If you wrap an entire structure with another .uninterruptible/.uninterruptibleMask, you will cover those gaps as well
   */

}
