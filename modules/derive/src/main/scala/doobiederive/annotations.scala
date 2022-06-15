package doobiederive

import scala.annotation.StaticAnnotation

object annotations {
  /**
   * Rename field name to `name`
   * @param name The new name
   */
  final class sqlName(name: String) extends StaticAnnotation

  /**
   * Query as Type `T` first, and then convert to type of the field
   * via typeclasses
   * @tparam T The proxy type
   */
  final class proxy[T] extends StaticAnnotation
}