
import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

package object doobiederive {

  @compileTimeOnly("Cannot expand derivedoobie")
  class derivedoobie extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro derive.impl
  }

  object derive {

    sealed trait Attribute
    final case class Rename(name: Any) extends Attribute
    final case class Proxy(ty: Any) extends Attribute

    def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._

      case class LocalAnnotation(typeName: String, typeArgs: List[Tree], arguments: List[Tree])


      /**
       * Represent fields in a case class
       *
       * @param originalField The original name of the field
       * @param originType    The type of the field
       * @param defaultValue  The default value of the field (if any)
       */
      case class MappedField(
                              annotations: List[LocalAnnotation],
                              attributes: List[Attribute],
                              originalField: TermName,
                              originType: Tree,
                              defaultValue: Option[Tree])
      {
        def fieldName: Any = {
          (attributes collectFirst {
            case Rename(value) => value
          }).getOrElse(originalField)
        }

        def mappedType: Tree = {
          val alt = attributes.collectFirst {
            case Proxy(ty) =>
              ty.asInstanceOf[Tree]
          }

          alt.getOrElse(originType)
        }
      }

      /** We are constructing the typeclass of a case class by mapping from its
       *  tuple counter part, which the typeclass is provided by doobie already.
       *
       *  Since the field itself in at the `n` position of the overall case class,
       *  we will need `n` to access the n-th element of the tuple we deriving from,
       *  e.g. $from._$n
       */
      def applyGenerator(field: MappedField, from: Tree, n: Int) = {
        /* In normal cases, we just summon the typeclasses, but in the case
         * that the type of the field (T) is mapped to Json, we will extra
         * Logic to unpack the value from Json
         */
        import field._

        /* accessor to the N-th element from the tuple $from */
        val tn = TermName(s"_$n")

        field.attributes.collectFirst {
          case Proxy(ty) =>
            println(s"ty: ${ty}")
            val tyy = ty.asInstanceOf[Tree]
            q"implicitly[Proxy[$tyy, $originType]].convert($from.$tn)"
        } getOrElse(q"$from.$tn")
      }

      def modifiedClass(classDecl: ClassDef, compDecl: Option[ModuleDef]) = {
        val (className, fields, bases, body) = try {
          val q"case class $className(..$fields) extends ..$bases { ..$body }" = classDecl
          (className, fields, bases, body)
        } catch {
          case _: MatchError =>
            c.abort(c.enclosingPosition, "Annotation only supported on case class")
        }

        val mappedFields = fields map {
          case field: ValDef =>
            val annotations = field.mods.annotations map { annotation =>
              val __base = annotation.children.head
              val tpe    = __base.children.head.children.head
              val types  = tpe.children
              val base   = if (types.isEmpty) tpe else types.head
              val targs  = if (types.isEmpty) List.empty else types.drop(1)
              val args   = annotation.children.drop(1)
              LocalAnnotation(base.toString(), targs, args)
            }

            val attributes = annotations collect {
              case LocalAnnotation("sqlName", _, args) =>
                Rename(args.head)
              case LocalAnnotation("proxy", tys, _) =>
                Proxy(tys.head)
            }

            MappedField(annotations, attributes, field.name, field.children.head,
              if (field.children.length > 1) Some(field.children.drop(1).head) else None)
        }

        val initializerList = mappedFields.zipWithIndex map { pair =>
          val (value, index) = pair
          applyGenerator(value, Ident(TermName("tuple")), index + 1)
        }

        val creation = Apply(Ident(className.toTermName), initializerList.toList)

        val columns = mappedFields.map(_.fieldName) map {
          case literal: Literal => literal
          case termName: TermName => Literal(Constant(termName.toString))
          case other =>
            c.abort(c.enclosingPosition, s"fieldName ($other) is neither literal or termname")
        }

        val tupleCount = mappedFields.length

        val injectionType = AppliedTypeTree(
          Select(Ident(TermName("scala")), TypeName(s"Tuple$tupleCount")), mappedFields.map(_.mappedType))

        val instance = q"""
        case class $className(..$fields) extends ..$bases {
          ..$body
        }
        """

        val compInject = q"""
        def doobieGetSQL: Read[$className] = Read[$injectionType] map { tuple =>
          $creation
        };
        """

        val comp = compDecl map { decl =>
          val q"object $obj extends ..$bases { ..$body }" = decl
          q"""
          object $obj extends ..$bases {
            ..$body
            val sqlColumns = $columns
            val sqlColumnsBatch: String = sqlColumns.map("\"" + _ + "\"").mkString(", ")
            $compInject
          }
          """
        } getOrElse {
          q"""
          object ${className.toTermName} {
            val sqlColumns = $columns
            val sqlColumnsBatch: String = sqlColumns.map("\"" + _ + "\"").mkString(", ")
            $compInject
          }
          """
        }

        c.Expr[Any](Block(instance :: comp :: Nil, Literal(Constant(()))))
      }

      annottees map (_.tree) match {
        case (classDecl: ClassDef) :: Nil =>
          modifiedClass(classDecl, None)
        case (classDecl: ClassDef) :: (compDecl: ModuleDef) :: Nil =>
          modifiedClass(classDecl, Some(compDecl))
      }
    }
  }
}
