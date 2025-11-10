package part_2_effects

import zio.{Trace, UIO, Unsafe, ZIO, *}

object ZIOApps {

  val meaningOfLife: UIO[Int] = ZIO.succeed(42)

  // clunky and unsafe
  def main(args: Array[String]): Unit = {
    val runtime = Runtime.default
    given trace: Trace = Trace.empty
    Unsafe.unsafeCompat { unsafe =>
      given u: Unsafe = unsafe
      println(runtime.unsafe.run(meaningOfLife))
    }
  }

  object BetterApp extends ZIOAppDefault {
    // provides runtime, trace, ......
    override def run = ZIOApps.meaningOfLife
  }

  // Not needed
  object ManualApp extends ZIOApp {
    override implicit def environmentTag = ???
    override type Environment = this.type
    override def bootstrap = ???
    override def run = ???
  }
}
