package doobiederive

trait Proxy[A, B] {
  def convert(_a: A): B
  def map[C](f: B => C): Proxy[A, C] = aValue => f(this.convert(aValue))
}
