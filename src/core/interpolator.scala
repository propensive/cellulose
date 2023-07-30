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

import language.experimental.captureChecking

extension (inline ctx: StringContext)
  transparent inline def codl(inline parts: Any*): CodlDoc = ${Codl.Prefix.expand('ctx, 'parts)}

extension [T](value: T)(using serializer: CodlSerializer[T], deserializer: CodlDeserializer[T])
  def codl: CodlDoc = CodlDoc(IArray.from(serializer.serialize(value).flatten), deserializer.schema, 0)
