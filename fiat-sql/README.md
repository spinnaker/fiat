## Configuring SQL store for fiat

#### MySQL:

```yaml
sql:
  enabled: true
  baseUrl: jdbc:mysql://localhost:3306/fiat
  connectionPools:
    default:
      jdbcUrl: ${sql.baseUrl}?useSSL=false&serverTimezone=UTC
      user: 
      password:
  migration:
    jdbcUrl: ${sql.baseUrl}?useSSL=false&serverTimezone=UTC
    user: 
    password:

permissionsRepository:
  redis:
    enabled: false
  sql:
    enabled: true
```

#### PostgreSQL:
```yaml
sql:
  enabled: true
  baseUrl: jdbc:postgresql://localhost:5432/fiat
  connectionPools:
    default:
      jdbcUrl: ${sql.baseUrl}
      dialect: POSTGRES
      user: 
      password:
  migration:
    jdbcUrl: ${sql.baseUrl}
    user: 
    password:

permissionsRepository:
  redis:
    enabled: false
  sql:
    enabled: true
```

## Migrating from Redis to SQL

Migrating without downtime from Redis to SQL is a three-step process:

1. Deploy Fiat with the `DualPermissionsRepository` writing to both Redis and SQL.
2. Run Fiat for a while to allow the data to migrate.
3. Once all permissions have been migrated, disable the `DualPermissionsRepository`


### Enable DualPermissionsRepository

```yaml
permissionsRepository:
  dual:
    enabled: true
    primaryClass: com.netflix.spinnaker.fiat.permissions.SqlPermissionsRepository
    previousName: com.netflix.spinnaker.fiat.permissions.RedisPermissionsRepository
  redis:
    enabled: true
  sql:
    enabled: true
```

Note that both repositories are enabled. Fiat will fail to start up if the `DualPermissionsRepository` is misconfigured.

## Deploy Fiat

Deploy Fiat as usual and leave running for at least twice the typical session duration of your users. This allows for no interruption to currently logged in users.

## Disable DualPermissionsRepository

Once all the permissions have been migrated, you can deploy Fiat without `DualPermissionsRepository`.
