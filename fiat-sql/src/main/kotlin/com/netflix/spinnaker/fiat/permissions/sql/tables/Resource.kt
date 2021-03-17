/*
 * Copyright 2021 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.permissions.sql.tables

import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.permissions.sql.converters.ResourceTypeConverter
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import org.jooq.impl.TableRecordImpl

class ResourceTableRecord() : TableRecordImpl<ResourceTableRecord>(ResourceTable.RESOURCE) {

}

class ResourceTable(
    alias: Name,
    child: Table<out Record>?,
    path: ForeignKey<out Record, ResourceTableRecord>?,
    aliased: Table<ResourceTableRecord>?,
    parameters: Array<Field<*>?>?
) : TableImpl<ResourceTableRecord>(
    alias,
    null,
    child,
    path,
    aliased,
    parameters,
    DSL.comment(""),
    TableOptions.table()
) {

    companion object {
        val RESOURCE = ResourceTable()
    }

    override fun getRecordType(): Class<ResourceTableRecord> = ResourceTableRecord::class.java

    val RESOURCE_TYPE: TableField<ResourceTableRecord, ResourceType> = createField(DSL.name("resource_type"), SQLDataType.VARCHAR(255).nullable(false), this, "", ResourceTypeConverter())
    val RESOURCE_NAME: TableField<ResourceTableRecord, String> = createField(DSL.name("resource_name"), SQLDataType.VARCHAR(255).nullable(false), this, "")
    val BODY: TableField<ResourceTableRecord, String> = createField(DSL.name("body"), SQLDataType.LONGVARCHAR.nullable(false), this, "")

    private constructor(alias: Name, aliased: Table<ResourceTableRecord>?): this(alias, null, null, aliased, null)

    constructor(): this(DSL.name("fiat_resource"), null)

    override fun `as`(alias: String): ResourceTable = ResourceTable(DSL.name(alias), this)
    override fun `as`(alias: Name): ResourceTable = ResourceTable(alias, this)
}
