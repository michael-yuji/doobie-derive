# doobie-derive

For scala 2, this package contains a macro annotation to implement `doobie` `Get` typeclasses. You still have to import the corresponding packages yourself.

There are a few annotation available to decode columns storing custom formats, for example you may be storing json in a sql table, and you still want to have a typesafe model without wrapper classes. You can use the provided `@proxy[T]` annotation which provides such functionality, an example implementation can be found in the `circe` module. 

A few properties are also derived, `sqlColumns: List[String]` and `sqlColumnsBatch: String`. `sqlColumns` are list of fieldNames and `sqlColumnsBatch` is a comma separated string of field names.
This package also implemented a `sqlx` string interpolator, which allow user to mixin raw string to sql query, similar to `slick`.

# Example
```scala
// you still need to import doobie related packages yourself
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import doobiederive._
// circe json support
import doobie.derive.circe._

// circe
import io.circe._
import io.circe.parser._
  
object Example {
  case class MyItem(foo: String, bar String)

  // we need to know how to convert `MyItem` from Json
  implicit def myItemDecoder: Decoder[MyItem] = ...
  @derivedoobie
  case class TestStruct(
    id: Int,
    /// supports default value
    name: String = "undefined name",
    /// array is natively supported by the doobie postgres
    numbers: List[Int],
    /// json_item stored in the database as Json document
    @proxy[Json] json_item: MyItem
  )

  def queryTestItems = {
    // field name order matter
    sql"select id, name, numbers, json_item from test_structs".query[TestStruct]
  }
  
  def queryTestItems2 = {
    import doobiederive.interpolate._
    /// the sqlColumsBatch has value of "id,name,numbers,json_item"
    sqlx"select #${TestStruct.sqlColumnsBatch} from test_structs".query[TestStruct]
  }
  
  def complicatedQuery = {
    import doobiederive.interpolate._
    val labeledFieldNames = TestStruct.sqlColumns.map(fieldName => s"t.$fieldName").toList
    sqlx"""
      -- let's assume we have another table `random_struct` with a text column `blah` here
      select count(*), #$labeledFieldNames, x.blah
        from test_structs t inner join random_struct x
    """.query[(Int, TestStruct, String)]
  }
}
```
