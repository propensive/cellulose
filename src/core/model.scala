/*
    Cellulose, version [unreleased]. Copyright 2023 Jon Pretty, Propensive OÜ.

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
import digression.*
import gossamer.*
import contextual.*
import spectacular.*
import eucalyptus.*
import dissonance.*
import chiaroscuro.*

import java.io as ji

import language.dynamics

object CodlNode:
  given Debug[CodlNode] = _.data.option.fold(t"!"): data =>
    t"${data.key}[${data.children.map(_.debug).join(t",")}]"

  val empty: CodlNode = CodlNode()
  def apply(key: Text)(child: CodlNode*): CodlNode = CodlNode(Data(key, IArray.from(child)))
  
  given Contrast[CodlNode] = (left, right) =>
    if left == right then Accordance.Accord(left.debug) else
      val comparison = IArray.from:
        diff(left.children, right.children).rdiff(_.id == _.id).changes.map:
          case Par(_, _, v) => v.mm(_.key).or(t"—") -> Accordance.Accord(v.debug)
          case Ins(_, v)    => v.mm(_.key).or(t"—") -> Accordance.Discord(t"—", v.debug)
          case Del(_, v)    => v.mm(_.key).or(t"—") -> Accordance.Discord(v.debug, t"—")
          
          case Sub(_, v, lv, rv) =>
            if lv.mm(_.key) == rv.mm(_.key) then lv.mm(_.key).or(t"—") -> lv.contrastWith(rv)
            else t"[key]" -> Accordance.Discord(lv.mm(_.key).or(t"—"), rv.mm(_.key).or(t"—"))

      Accordance.Collation(comparison, left.key.or(t"—"), right.key.or(t"—"))
  
case class CodlNode(data: Maybe[Data] = Unset, meta: Maybe[Meta] = Unset) extends Dynamic:
  def key: Maybe[Text] = data.mm(_.key)
  def empty: Boolean = unsafely(data.unset || data.assume.children.isEmpty)
  def blank: Boolean = data.unset && meta.unset
  def schema: Maybe[CodlSchema] = data.mm(_.schema)
  def layout: Maybe[Layout] = data.mm(_.layout)
  def id: Maybe[Text] = data.mm(_.id)
  def uniqueId: Maybe[Text] = data.mm(_.uniqueId)
  def children: IArray[CodlNode] = data.mm(_.children).or(IArray[CodlNode]())
  def paramValue: Maybe[Text] = if children.isEmpty then key else Unset
  def structValue: Maybe[Text] = if children.size == 1 then children.head.paramValue else Unset
  def fieldValue: Maybe[Text] = paramValue.or(structValue)
  def promote(n: Int) = copy(data = data.mm(_.promote(n)))

  def apply(key: Text): List[Data] = data.fm(List[CodlNode]())(_(key)).map(_.data).collect:
    case data: Data => data

  def selectDynamic(key: String): List[Data] throws MissingValueError =
    data.option.getOrElse(throw MissingValueError(key.show)).selectDynamic(key)
  
  def applyDynamic(key: String)(idx: Int = 0): Data throws MissingValueError = selectDynamic(key)(idx)

  def untyped: CodlNode =
    val data2 = data.mm { data => Data(data.key, children = data.children.map(_.untyped)) }
    CodlNode(data2, meta)
  
  def uncommented: CodlNode =
    val data2 = data.mm { data => Data(data.key, children = data.children.map(_.uncommented), Layout.empty, data.schema) }
    CodlNode(data2, Unset)

  def wiped: CodlNode = untyped.uncommented
  
  // override def toString(): String =
  //   if !children.isEmpty then s"$key[${children.mkString(" ")}]" else key.mm(_.s).or:
  //     meta.toString

object CodlDoc:
  def apply(nodes: CodlNode*): CodlDoc = CodlDoc(IArray.from(nodes), CodlSchema.Free, 0)

  given Debug[CodlDoc] = _.serialize
  
  given Assimilable[CodlDoc] = _.schema == _.schema
  //given Contrast[CodlDoc] = Contrast.derived[CodlDoc]

  inline given Contrast[CodlDoc] = new Contrast[CodlDoc]:
    def apply(left: CodlDoc, right: CodlDoc) =
      inline if left == right then Accordance.Accord(left.debug) else
        val comparison = IArray.from:
          (t"[schema]", left.schema.contrastWith(right.schema)) +:
          (t"[margin]", left.margin.contrastWith(right.margin)) +:
          diff(left.children, right.children).rdiff(_.id == _.id).changes.map:
            case Par(_, _, v)      => v.mm(_.key).or(t"—") -> Accordance.Accord(v.debug)
            case Ins(_, v)         => v.mm(_.key).or(t"—") -> Accordance.Discord(t"—", v.debug)
            case Del(_, v)         => v.mm(_.key).or(t"—") -> Accordance.Discord(v.debug, t"—")
            case Sub(_, v, lv, rv) =>
              val key = if lv.mm(_.key) == rv.mm(_.key) then lv.mm(_.key).or(t"—") else t"${lv.mm(_.key).or(t"—")}/${rv.mm(_.key).or(t"—")}"
              key -> lv.contrastWith(rv)
        
        Accordance.Collation(comparison, t"", t"")

case class CodlDoc(children: IArray[CodlNode], schema: CodlSchema, margin: Int, body: LazyList[Text] = LazyList())
extends Indexed:
  //override def toString(): String = s"[[${children.mkString(" ")}]]"
  
  override def equals(that: Any) = that.matchable(using Unsafe) match
    case that: CodlDoc => schema == that.schema && margin == that.margin && children.sameElements(that.children)
    case _         => false

  override def hashCode: Int = children.toSeq.hashCode ^ schema.hashCode ^ margin.hashCode

  def layout: Layout = Layout.empty
  def paramIndex: Map[Text, Int] = Map()

  def merge(input: CodlDoc): CodlDoc =
    
    def cmp(x: CodlNode, y: CodlNode): Boolean =
      if x.uniqueId.unset || y.uniqueId.unset then
        if x.data.unset || y.data.unset then x.meta == y.meta
        else x.data == y.data
      else x.id == y.id

    def recur(original: IArray[CodlNode], updates: IArray[CodlNode]): IArray[CodlNode] =
      val changes = diff[CodlNode](children, updates, cmp).edits
      
      val nodes2 = changes.foldLeft(List[CodlNode]()):
        case (nodes, Del(left, value))         => nodes
        case (nodes, Ins(right, value))        => value :: nodes
        case (nodes, Par(left, right, value)) =>
          val orig: CodlNode = original(left)
          val origData: Data = orig.data.or(???)
          
          if orig.id.unset || updates(right).id.unset then orig :: nodes
          else
            val children2 = recur(origData.children, updates(right).data.or(???).children)
            // FIXME: Check layout remains safe
            orig.copy(data = origData.copy(children = children2)) :: nodes
      
      IArray.from(nodes2.reverse)
    
    copy(children = recur(children, input.children))


  def as[T](using codec: Codec[T]): T throws CodlReadError = codec.deserialize(List(this))
  def uncommented: CodlDoc = CodlDoc(children.map(_.uncommented), schema, margin, body)
  def untyped: CodlDoc = CodlDoc(children.map(_.untyped), CodlSchema.Free, margin, body)
  def wiped = uncommented.untyped

  def binary(using Log): Text =
    val writer: ji.Writer = ji.StringWriter()
    Bin.write(writer, this)
    writer.toString().show

  def serialize: Text =
    val writer: ji.Writer = ji.StringWriter()
    Printer.print(writer, this)
    writer.toString().show

object Data:
  given [T: Codec]: Insertion[List[Data], T] =
    value => summon[Codec[T]].serialize(value).head.to(List).map(_.data).collect { case data: Data => data }

  given debug: Debug[Data] = data => t"Data(${data.key}, ${data.children.length})"

case class Data(key: Text, children: IArray[CodlNode] = IArray(), layout: Layout = Layout.empty,
                    schema: CodlSchema = CodlSchema.Free)
extends Indexed:

  lazy val paramIndex: Map[Text, Int] =
    (0 until layout.params.min(schema.paramCount)).map: idx =>
      schema.subschemas(idx).key -> idx
    .to(Map)

  def uniqueId: Maybe[Text] = schema.subschemas.find(_.schema.arity == Arity.Unique) match
    case Some(CodlSchema.Entry(name: Text, schema)) =>
      paramIndex.get(name).map(children(_).fieldValue).getOrElse(Unset)
    case _ => Unset

  def id: Maybe[Text] = schema.subschemas.find(_.schema.arity == Arity.Unique) match
    case Some(CodlSchema.Entry(name: Text, schema)) =>
      index(name).mm(_.headOption.maybe).mm(children(_).fieldValue)
    case _ => key

  def promote(n: Int): Data = copy(layout = layout.copy(params = n))

  def has(key: Text): Boolean = index.contains(key) || paramIndex.contains(key)
  //override def toString(): String = s"[${children.mkString(" ")}]"
  
  override def equals(that: Any) = that.matchable(using Unsafe) match
    case that: Data => key == that.key && children.sameElements(that.children) && layout == that.layout &&
                           schema == that.schema
    case _          => false

  override def hashCode: Int = key.hashCode ^ children.toSeq.hashCode ^ layout.hashCode ^ schema.hashCode


case class Meta(blank: Int = 0, comments: List[Text] = Nil, remark: Maybe[Text] = Unset, tabs: Tabs = Tabs())
object Layout:
  final val empty = Layout(0, false)

case class Layout(params: Int, multiline: Boolean)
case class Tabs(stops: TreeSet[Int] = TreeSet())

trait Indexed extends Dynamic:
  def children: IArray[CodlNode]
  def schema: CodlSchema
  def layout: Layout
  def paramIndex: Map[Text, Int]

  lazy val index: Map[Text, List[Int]] =
    children.map(_.data).zipWithIndex.foldLeft(Map[Text, List[Int]]()):
      case (acc, (data: Data, idx)) =>
        if idx < layout.params then schema.param(idx).fm(acc): entry =>
          acc.upsert(entry.key, _.fm(List(idx))(idx :: _))
        else acc.upsert(data.key, _.fm(List(idx))(idx :: _))
      case (acc, _) => acc
    .view.mapValues(_.reverse).to(Map)

  protected lazy val idIndex: Map[Text, Int] =
    def recur(idx: Int, map: Map[Text, Int] = Map()): Map[Text, Int] =
      if idx < 0 then map else recur(idx - 1, children(idx).id.fm(map)(map.updated(_, idx)))
    
    recur(children.length - 1)

  def ids: Set[Text] = idIndex.keySet

  def apply(idx: Int = 0): CodlNode throws MissingIndexValueError =
    children.lift(idx).getOrElse(throw MissingIndexValueError(idx))
  
  def apply(key: Text): List[CodlNode] = index.get(key).getOrElse(Nil).map(children(_))

  def get(key: Text): List[Indexed] = paramIndex.lift(key) match
    case None      => index.lift(key) match
      case None       => Nil
      case Some(idxs) => unsafely(idxs.map(children(_).data.assume))
    case Some(idx) => List.range(idx, layout.params).map: idx =>
                        Data(key, IArray(unsafely(children(idx))), Layout.empty, CodlSchema.Free)

  def selectDynamic(key: String): List[Data] throws MissingValueError =
    index(key.show).map(children(_).data).collect:
      case data: Data => data
  
  def applyDynamic(key: String)(idx: Int = 0): Data throws MissingValueError = selectDynamic(key)(idx)
