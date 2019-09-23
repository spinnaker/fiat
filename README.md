Spinnaker Auth Service
----------------------

[![Build Status](https://api.travis-ci.org/spinnaker/fiat.svg?branch=master)](https://travis-ci.org/spinnaker/fiat)

```
   ____ _         ____ __    ___               _            ______                  _
  / __/(_)__ __  /  _// /_  / _ | ___ _ ___ _ (_)___       /_  __/____ ___ _ _  __ (_)___
 / _/ / / \ \ / _/ / / __/ / __ |/ _ `// _ `// // _ \ _     / /  / __// _ `/| |/ // /(_-<
/_/  /_/ /_\_\ /___/ \__/ /_/ |_|\_, / \_,_//_//_//_/( )   /_/  /_/   \_,_/ |___//_//___/
                                /___/                |/
```

Fiat is the authorization server for the Spinnaker system.

It exposes a RESTful interface for querying the access permissions for a particular user. It currently supports three kinds of resources:
* Accounts
* Applications
* Service Accounts

---

### Accounts
Accounts are setup within Clouddriver and queried by Fiat for its configured `requiredGroupMembership` restrictions.

### Applications
Applications are the combination of config metadata pulled from Front50 and server group names (e.g., application-stack-details). Application permissions sit beside application configuration in S3/Google Cloud Storage.

### Service Accounts
Fiat Service Accounts are groups that act as a user during automated triggers (say, from a GitHub push or Jenkins build). Authorization is built in by making the service account a member of a group specified in `requiredGroupMembership`.

---

### Resource Group Permissions
In addition to storing resource-level permissions, fiat allows us to configure resource-group-level permissions. Group permissions cover all resource that comply to a certain restriction specified by the group. For example, we might have a group permission that covers all applications whose names starts with the prefix `abc*`.

When speaking about group permissions, there are three things to consider:
1. The resource type that the group applies to: Currently, we allow storing resource groups for all resource types, but only process those belonging to applications.
2. The group type: This specifies the way in which a resource group determines whether a resource belongs to it or not. The only type we currently have is prefix group type, which determines whether a resource belongs to the group depending on whether the name of the resource starts with the group prefix.
3. The group resolution strategy: This specifies how we handle the case when one resource belongs to multiple groups. The only current implementation is an "additive" strategy, which adds all the permissions for the groups that contain the resource.
    
    For example, if we are using prefix groups, and have two groups:
    - `*`: That sets the `WRITE` permission to user group `group1` for all applications
    - `abc*`: That sets the `WRITE` permission to user group `group2` for applications starting with `abc`
    
    And we have permissions for application `abcdefgh` that sets the `WRITE` permission to user group `group3`. Then application `abcdefgh` will have `WRITE` permission for user groups `group1`, `group2` and `group3`

---

### User Role/Authorization Providers
Currently supported user role providers are:
* Google Groups (through a Google Apps for Work organization)
* GitHub Teams
* LDAP
* File based role provider

---

### Modular builds
By default, Fiat is built with all authorization providers included. To build only a subset of
providers, use the `includeProviders` flag:
 ```
./gradlew -PincludeProviders=google-groups,ldap clean build
```
 You can view the list of all providers in `gradle.properties`.

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 7103.  The JVM will _not_ wait for the debugger
to be attached before starting Fiat; the relevant JVM arguments can be seen and modified as needed in `build.gradle`.
