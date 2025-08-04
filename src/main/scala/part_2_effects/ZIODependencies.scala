package part_2_effects

import zio.*

object ZIODependencies extends ZIOAppDefault {

  // app to subscribe users to newsletter
  case class User(name: String, email: String)

  class UserSubscription(emailService: EmailService, userDatabase: UserDatabase) {
    def subscribeUser(user: User): Task[Unit] =
      for {
        _ <- emailService.email(user)
        _ <- userDatabase.insert(user)
      } yield ()
  }
  class EmailService {
    def email(user: User): Task[Unit] =
      ZIO.succeed(s"You've just been subscribed to Rock the JVM, Welcome, ${user.name}").unit
  }
  class UserDatabase(connectionPool: ConnectionPool) {
    def insert(user: User): Task[Unit] = for {
      conn <- connectionPool.get
      _ <- conn.runQuery(s"insert into subscribers(name, email) values (${user.name}, ${user.email})")
    } yield ()
  }

  class ConnectionPool(nConnections: Int) {
    def get: Task[Connection] =
      ZIO.succeed(println("Acquired connection")) *> ZIO.succeed(Connection())
  }

  case class Connection() {
    def runQuery(query: String): Task[Unit] =
      ZIO.succeed(println(s"Executing query: $query"))
  }

  object UserSubscription {
    def create(emailService: EmailService, userDatabase: UserDatabase) =
      new UserSubscription(emailService, userDatabase)
  }

  object EmailService {
    def create(): EmailService = new EmailService
  }

  object UserDatabase {
    def create(connectionPool: ConnectionPool) =
      new UserDatabase(connectionPool)
  }

  object ConnectionPool {
    def create(nConnections: Int) =
      new ConnectionPool(nConnections)
  }

  val subscriptionService = ZIO.succeed( // This pattern is known as dependency injection......many ways to do this
    UserSubscription.create(
      EmailService.create(),
      UserDatabase.create(
        ConnectionPool.create(10)
      )
    )
  )

  /**
   * Using companion objects for factory methods leads to clean DI but has a number of objects
   * - does not scale for many services -- nightmare to debug
   * - DI can be a 100x worse - need to know where everything comes form and can still rely on the developer organising all these services
   *  - pass dependencies partially
   *  - not having all dependencies in the same place
   *  - passing dependencies multiple times can lead to errors in your code. Can leak resources that you are not aware of.
   */


  def subscribe(user: User): ZIO[Any, Throwable, Unit] = for {
    sub  <- subscriptionService // service is instantiated at the point of call
    _ <- sub.subscribeUser(user)
  } yield ()

  // risk leaking resources if you subscribe multiple users in the same program
  val program = for {
    _ <- subscribe(User("User123", "123@lalala.com"))
    _ <- subscribe(User("Potato", "potato@kebab.com"))
  } yield ()

  // alternative
  def subscribe_v2(user: User): ZIO[UserSubscription, Throwable, Unit] = for {
    sub <- ZIO.service[UserSubscription] // ZIO[UserSubscription, Nothing, UserSubscription]
    _ <- sub.subscribeUser(user)
  } yield ()

  val program_v2 = for {
    _ <- subscribe_v2(User("User123", "123@lalala.com"))
    _ <- subscribe_v2(User("Potato", "potato@kebab.com"))
  } yield ()

  /**
   * Second approach
   * - We don't need to care about dependencies until the end of the world
   * - All ZIOs requiring this dependency will use the same instance
   * - Can use different instances of the same type for different needs (e.g testing)
   * - layers can be created and composed much like regular ZIOs + rich API
   *
   */

  def run = program_v2.provideLayer(
    ZLayer.succeed(
      UserSubscription.create(
        EmailService.create(),
        UserDatabase.create(
          ConnectionPool.create(10)
        )
      )
    )
  ) //provide in Scala 2 - potential macro issues

}
