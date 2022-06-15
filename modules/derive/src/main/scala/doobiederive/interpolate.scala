package doobiederive

import doobie._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

object interpolate {

  implicit class sqlx(sc: StringContext) {
    implicit def sqlx(sc: Any*): Fragment = macro sqlxImpl
  }

  def sqlxImpl(c: whitebox.Context)(sc: c.Expr[Any]*): c.Expr[Fragment] = {
    import c.universe._
    val q"$foo($scc)" = c.prefix.tree

    val lastComponent = scc.children.takeRight(1).head

    val result = scc.children.drop(1).zip(sc) match {
      case Nil => q"Fragment.const($lastComponent)"
      case nonemptyList =>
        val unquoted = nonemptyList.map {
          case pair @ ((Literal(Constant(ss))), b) if ss.asInstanceOf[String].endsWith("#") =>
            q"""
            Fragment.const(${ss.asInstanceOf[String].dropRight(1)}) ++ Fragment.const($b)
            """
          case pair @ ((Literal(Constant(ss))), b) =>
            q"""
            Fragment.const(${ss.asInstanceOf[String]}) ++ StringContext("", "").fr($b)
            """
        }

        q"${ unquoted reduce { (t, a) => q"$t ++ $a" } } ++ Fragment.const($lastComponent)"
    }

    c.Expr(result)
  }
}
