package part_1_recap

object Variance {

  // OOP - substitution and subtyping
  class Animal
  class Dog(name: String) extends Animal

  // variance question for List: if Dog <: Animal, then should List[Dog] <: List[Animal]?
  // intuitively....yes

  // Yes -> Covariance
  val lassie = new Dog("Lassie")
  val hachi = new Dog("Hachi")
  val laika = new Dog("Laika")

  val anAnimal: Animal = lassie
  val someAnimal: List[Animal] = List(lassie, hachi, laika)

  class MyList[+A] // MyList is COVARIANT in A
  val myAnimalList: MyList[Animal] = new MyList[Dog]

  // No -> the type in question is invariant. Two semi groups for two different types have no relationships between them
  trait Semigroup[A] {
    def combine(x: A, y: A): A
  }

  // all generics in Java
//  val aJavaList: java.util.ArrayList[Animal] = new util.ArrayList[Dog]() // Arraylist type in Java is Invariant

  // No / the other way round - Contravariance
  trait Vet[-A] {
    def heal(animal: A): Boolean
  }

  // Vet[Animal] is more general than a Vet[Dog] // can treat any animal including dogs
  // Dog <: Animal, then Vet[Dog] >: Vet[Animal]
  val myVet: Vet[Dog] = new Vet[Animal] {
    override def heal(animal: Animal) = {
      println("Here you go, you're good now...")
      true
    }
  }

  val healingLassie = myVet.heal(lassie)

  /*
   * Rule of thumb:
   * - if the type produces or retrieves values of type A (e.g. lists), then the type should be covariant
   * - if the type consumes or acts on values of type A (e.g. a vet), then the type should be contravariant
   * - otherwise, Invariant
   */

  /**
   * Problems - Variance Positions
   *
   */
  /*
    class Cat extends Animal
    class Vet2[-A](val favouriteAnimal: A) -- leads to an error <---- the types of val fields are in covariant positions

    val garfield = new Cat
    val theVet: Vet2[Animal] = new Vet2[Animal](garfield)
    val dogVet: Vet2[Dog] = theVet
    val favAnimal: Dog = dogVet.favouriteAnimal // must be a Dog - type conflict....
   */

  // var fields aer also in covariant positions (same reason)

  /*
    class MutableContainer[+A](var contents: A) <-- types of vars are in a contravariant position
    val containerAnimal: MutableContainer[Animal] = new MutableContainer[Dog](new Dog)

    containerAnimal.contents = new Cat // type conflict

  */

  // types of method argument in contravariant position
  /*
  class MyList2[+A] {
    def add(element: A): MyList[A]
  }

  val animals: MyList2[Animals] = new MyList2[Cat]
  val biggerListOfAnimals: MyList2[Animal] = animals.add(new Dog) // type conflict
  */

  // solution: widen the type argument
  class MyList2[+A] {
    def add[B >: A](element: B): MyList[B] = ???
    //-----^^^^^^^^ B must be a super type of A
  }

  // method return types are in covariant position (incompatible with contravariant types)
  /*
    abstract class Vet2[-A] {
      def rescueAnimal(): A
    }

    val vet: Vet2[Animal] = new Vet2[Animal] {
      def rescueAnimal(): Animal = new Cat
    }

    val lassieVet: Vet2[Dog] = vet
    val rescueDof: Dog = lassieVet.rescueAnimal() // must return a Dog, but it returns a Cat ---> type conflict
   */

  abstract class Vet2[-A] {
    def rescueAnimal[B <: A](): B //Implementation may be a completely different thing but at least it makes the compiler happy
  }

  def main(args: Array[String]): Unit = {

  }


}
