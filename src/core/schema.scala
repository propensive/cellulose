/*
    Cellulose, version 0.4.0. Copyright 2022-23 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package cellulose

import rudiments.*
import gossamer.*
import eucalyptus.*

import java.io as ji

import language.experimental.pureFunctions
import language.dynamics

object Schema:

  object Entry:
    def apply(key: Text, schema: => Schema): Entry = new Entry(key, () => schema)
    def unapply(value: Entry): Option[(Text, Schema)] = Some(value.key -> value.schema)

  class Entry(val key: Text, getSchema: () => Schema):
    def required = schema.arity.required
    def variadic = schema.arity.variadic
    def unique = schema.arity.unique
    def schema: Schema = getSchema()
    def tuple: (Text, Schema) = key -> schema


  // FIXME
  object Free extends Struct(List(Entry(t"?", Field(Arity.Many))), Arity.Many):
    override def apply(key: Text): Maybe[Schema] = Free
    override def optional = Free
    override def toString(): String = "%"

  def apply(subschemas: List[(Text, Schema)]): Schema = Struct(subschemas.map(Entry(_, _)), Arity.Optional)

sealed trait Schema(val subschemas: IArray[Schema.Entry], val arity: Arity,
                        val validator: Maybe[Text -> Boolean])
extends Dynamic:
  import Schema.{Free, Entry}
  protected lazy val dictionary: Map[Maybe[Text], Schema] = subschemas.map(_.tuple).to(Map)
  
  lazy val keyMap: Map[Maybe[Text], Int] = subschemas.map(_.key).zipWithIndex.to(Map)

  def optional: Schema
  def entry(n: Int): Entry = subschemas(n)
  def parse(text: Text)(using Log): Doc throws AggregateError = Codl.parse(ji.StringReader(text.s), this)
  def apply(key: Text): Maybe[Schema] = dictionary.get(key).orElse(dictionary.get(Unset)).getOrElse(Unset)
  def apply(idx: Int): Entry = subschemas(idx)

  private lazy val fieldCount: Int = subschemas.indexWhere(!_.schema.is[Field]) match
    case -1    => subschemas.size
    case count => count
  
  private lazy val firstVariadic: Maybe[Int] = subschemas.indexWhere(_.schema.variadic) match
    case -1  => Unset
    case idx => idx
  
  lazy val paramCount: Int = firstVariadic.fm(fieldCount) { f => (f + 1).min(fieldCount) }
  private lazy val endlessParams: Boolean = firstVariadic.fm(false)(_ < fieldCount)

  def param(idx: Int): Maybe[Entry] =
    if idx < paramCount then subschemas(idx)
    else if endlessParams && paramCount > 0 then subschemas(paramCount - 1) else Unset

  def has(key: Maybe[Text]): Boolean = dictionary.contains(key)
  
  lazy val requiredKeys: List[Text] = subschemas.filter(_.required).map(_.key).sift[Text].to(List)
  
  export arity.{required, variadic, unique}

enum Arity:
  case One, AtLeastOne, Optional, Many, Unique

  def required: Boolean = this == One || this == Unique || this == AtLeastOne
  def variadic: Boolean = this == AtLeastOne || this == Many
  def unique: Boolean = !variadic

object Struct:
  def apply(arity: Arity, subschemas: (Text, Schema)*): Struct =
    Struct(subschemas.map(Schema.Entry(_, _)).to(List), arity)

case class Struct(structSubschemas: List[Schema.Entry], structArity: Arity = Arity.Optional)
extends Schema(IArray.from(structSubschemas), structArity, Unset):
  import Schema.{Free, Entry}
  
  def optional: Struct = Struct(structSubschemas, Arity.Optional)
  def uniqueIndex: Maybe[Int] = subschemas.indexWhere(_.schema.arity == Arity.Unique) match
    case -1  => Unset
    case idx => idx
  
  lazy val params: IArray[Entry] =
    def recur(subschemas: List[Entry], fields: List[Entry]): IArray[Entry] = subschemas match
      case Nil                                             => IArray.from(fields.reverse)
      case Entry(key, struct: Struct) :: _                 => recur(Nil, fields)
      case Entry(key, field: Field) :: _ if field.variadic => recur(Nil, Entry(key, field) :: fields)
      case Entry(key, field: Field) :: tail                => recur(tail, Entry(key, field) :: fields)

    recur(subschemas.to(List), Nil)

case class Field(fieldArity: Arity, fieldValidator: Maybe[Text -> Boolean] = Unset)
extends Schema(IArray(), fieldArity, fieldValidator):
  def optional: Field = Field(Arity.Optional, fieldValidator)