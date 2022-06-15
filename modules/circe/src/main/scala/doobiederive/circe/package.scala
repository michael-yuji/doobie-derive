package doobiederive

import doobiederive.Proxy
import io.circe.{Json, Decoder}

package object circe {
  implicit def jsonProxy[T](implicit decoder: Decoder[T]): Proxy[Json, T] =
    jsonValue => jsonValue.as[T].toOption.get

  implicit def jsonProxyOptT[T](implicit decoder: Decoder[T]): Proxy[Json, Option[T]] =
    jsonValue => jsonValue.as[T].toOption

  implicit def optJsonProxy[T](implicit decoder: Decoder[T]): Proxy[Option[Json], Option[T]] =
    optJsonValue => optJsonValue.flatMap(_.as[T].toOption)
}