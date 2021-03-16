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

package com.netflix.spinnaker.fiat.permissions.sql


import com.netflix.spinnaker.fiat.model.resources.ResourceType
import org.jooq.*
import org.jooq.Table
import org.jooq.impl.AbstractConverter
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import org.jooq.impl.TableRecordImpl

class Tables {
    companion object {
        val USER = UserTable()
        val PERMISSION = PermissionTable()
        val RESOURCE = ResourceTable()
    }
}

class UserTableRecord() : TableRecordImpl<UserTableRecord>(Tables.USER) {

}

class UserTable(
    alias: Name,
    child: Table<out Record>?,
    path: ForeignKey<out Record, UserTableRecord>?,
    aliased: Table<UserTableRecord>?,
    parameters: Array<Field<*>?>?
) : TableImpl<UserTableRecord>(
    alias,
    null,
    child,
    path,
    aliased,
    parameters,
    comment(""),
    TableOptions.table()
) {

    override fun getRecordType(): Class<UserTableRecord> = UserTableRecord::class.java

    val ID: TableField<UserTableRecord, String> = createField(name("id"), SQLDataType.VARCHAR(255).nullable(false), this, "")
    val ADMIN: TableField<UserTableRecord, Boolean> = createField(name("admin"), SQLDataType.BOOLEAN.nullable(false), this, "")
    val UPDATED_AT: TableField<UserTableRecord, Long> = createField(name("updated_at"), SQLDataType.BIGINT.nullable(false), this, "")

    private constructor(alias: Name, aliased: Table<UserTableRecord>?): this(alias, null, null, aliased, null)

    constructor(): this(name("fiat_user"), null)

    override fun `as`(alias: String): UserTable = UserTable(name(alias), this)
    override fun `as`(alias: Name): UserTable = UserTable(alias, this)
}

class PermissionTableRecord() : TableRecordImpl<PermissionTableRecord>(Tables.PERMISSION) {

}

class PermissionTable(
    alias: Name,
    child: Table<out Record>?,
    path: ForeignKey<out Record, PermissionTableRecord>?,
    aliased: Table<PermissionTableRecord>?,
    parameters: Array<Field<*>?>?
) : TableImpl<PermissionTableRecord>(
    alias,
    null,
    child,
    path,
    aliased,
    parameters,
    comment(""),
    TableOptions.table()
) {

    override fun getRecordType(): Class<PermissionTableRecord> = PermissionTableRecord::class.java

    val USER_ID: TableField<PermissionTableRecord, String> = createField(name("fiat_user_id"), SQLDataType.VARCHAR(255).nullable(false), this, "")
    val RESOURCE_TYPE: TableField<PermissionTableRecord, ResourceType> = createField(name("resource_type"), SQLDataType.VARCHAR(255).nullable(false), this, "", ResourceTypeConverter())
    val RESOURCE_NAME: TableField<PermissionTableRecord, String> = createField(name("resource_name"), SQLDataType.VARCHAR(255).nullable(false), this, "")

    private constructor(alias: Name, aliased: Table<PermissionTableRecord>?): this(alias, null, null, aliased, null)

    constructor(): this(name("fiat_permission"), null)

    override fun `as`(alias: String): PermissionTable = PermissionTable(name(alias), this)
    override fun `as`(alias: Name): PermissionTable = PermissionTable(alias, this)
}

class ResourceTableRecord() : TableRecordImpl<ResourceTableRecord>(Tables.RESOURCE) {

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
    comment(""),
    TableOptions.table()
) {


    override fun getRecordType(): Class<ResourceTableRecord> = ResourceTableRecord::class.java

    val RESOURCE_TYPE: TableField<ResourceTableRecord, ResourceType> = createField(name("resource_type"), SQLDataType.VARCHAR(255).nullable(false), this, "", ResourceTypeConverter())
    val RESOURCE_NAME: TableField<ResourceTableRecord, String> = createField(name("resource_name"), SQLDataType.VARCHAR(255).nullable(false), this, "")
    val BODY: TableField<ResourceTableRecord, String> = createField(name("body"), SQLDataType.LONGVARCHAR.nullable(false), this, "")

    private constructor(alias: Name, aliased: Table<ResourceTableRecord>?): this(alias, null, null, aliased, null)

    constructor(): this(name("fiat_resource"), null)

    override fun `as`(alias: String): ResourceTable = ResourceTable(name(alias), this)
    override fun `as`(alias: Name): ResourceTable = ResourceTable(alias, this)
}

class ResourceTypeConverter : AbstractConverter<String, ResourceType>(String::class.java, ResourceType::class.java) {

    override fun from(databaseObject: String?): ResourceType? {
        return when (databaseObject) {
            null -> null
            "${ResourceType.ACCOUNT}" -> ResourceType.ACCOUNT
            "${ResourceType.APPLICATION}" -> ResourceType.APPLICATION
            "${ResourceType.BUILD_SERVICE}" -> ResourceType.BUILD_SERVICE
            "${ResourceType.ROLE}" -> ResourceType.ROLE
            "${ResourceType.SERVICE_ACCOUNT}" -> ResourceType.SERVICE_ACCOUNT
            else -> throw IllegalArgumentException("unknown resource type $databaseObject")
        }
    }

    override fun to(userObject: ResourceType?): String? {
        return userObject?.toString()
    }

}