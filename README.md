# codescene-enterprise-pm-jira

A *Project Management* service which integrates JIRA with CodeScene Enterprise.

## Overview

This service:

- [x] Periodically syncs issues from JIRA
- [x] Stores issues in a local database
- [x] Syncs only issues with cost values set
- [x] Cost unit type can be configured
- [x] Supported *types of work* can be configured
- [x] *type of work* can be combinations of JIRA Labels and Issue Types
- [ ] A custom "fallback cost" can be configured where value is missing
- [ ] JIRA Labels can be mapped to types of work in configuration

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

### Running from the Command Line during development

Make sure that you have proper configuration file in place. See [Configuration Path](#configuration-path)

To start a web server for the application from the command line, run:

    # Use default port 3004 and default config file 'codescene-jira.yml'
    lein run
    
    # Use custom port 3001 and default config file
    PORT=3001 lein run
    
    # Provide custom config file
    CODESCENE_JIRA_CONFIG=/etc/codescene-jira.yml lein run

    # Override database path (defaults to db/codescene-enterprise-pm-jira)
    CODESCENE_JIRA_DATABASE_PATH=/var/lib/codescene/codescene-enterprise-pm-jira lein run

### Running in Tomcat for deployment

To start a web server in Tomcat, you need to specify the required JNDI context paths in the file `conf/Context.xml` in Tomcat 7.
Here's an example configuration that you want to add to `conf/Context.xml``:

    <Environment name="codescene/enterprise/pm/jira/config" value="/mydocs/codescene/codescene-jira.yml" type="java.lang.String"/>

    <Environment name="codescene/enterprise/pm/jira/dbpath" value="/mydocs/codescene/db/codescene-enterprise-pm-jira" type="java.lang.String"/>

When run in Tomcat, the application uses the paths above to resolve both the configuration file and the internal database for
JIRA synchronization data.

## Configuration

```yaml
sync:
  hour-interval: {number}
auth:
  service:
    username: {string}
    password: {string}
  jira:
    base-uri: {uri}
    username: {string}
    password: {string}
projects:
  - key: {jira-project-key}
    cost-unit:
      type: {minutes|points}
      format: #optional
        singular: {format-string}
        plural: {format-string}
    cost-field: {jira-field-name}
    supported-work-types: [{jira-label}]
    ticket-id-pattern: {regex-pattern}
```

The `supported-work-types` specify the JIRA labels and/or JIRA Issue Types you want to include in the analysis.
Please note that only types with the listed labels/type will be included in the analysis.

The cost-unit type has to be either `minutes` or `points` (e.g. story points).

### Example Configuration

```yaml
sync:
  hour-interval: 4 # sync every 4 hours
auth:
  service:
    username: johndoe
    password: somepwd
  jira:
    base-uri: https://jira.example.com
    username: jirauser
    password: jirapwd
projects:
  - key: CSE
    cost-unit:
      type: minutes
    cost-field: timeoriginalestimate
    supported-work-types:
      - Bug
      - Feature
      - Refactoring
      - Documentation
    ticket-id-pattern: (CSE-\d+)
  - key: DVP
    cost-unit:
      type: points
      format:
        singular: '%d point'
        plural: '%d points'
    cost-field: customfield_10006
    supported-work-types:
      - Bug
      - Feature
      - Refactoring
      - Documentation
    ticket-id-pattern: (DVP-\d+)
```

### Configuration Path

The configuration file path is resolved in the following order:

1. Environment variable `CODESCENE_JIRA_CONFIG`, if set.
1. JNDI context path `codescene/enterprise/pm/jira/config`, if set. Can be
   configured in Tomcat 7 in `conf/context.xml`, like this:
   ```
   <Environment name="codescene/enterprise/pm/jira/config"
     value="/etc/codescene/codescene-jira.yml"
     type="java.lang.String"/>
   ```
1. The file `codescene-jira.yml` in the current working directory.

If the configuration path doesn't point to a valid YAML file the service
fails to start.

## API

### `GET /api/1/projects/{id}`

Returns a project including all its synced items. Each item has an array of
numbers, 0 or 1, corresponding to the work types in `"workTypes"` of
the project. 1 means it has the work type, 0 means it doesn't.

#### Example Request

```bash
curl -i -X GET -H 'Accept: application/json' -u 'user:pass' https://jira-integration.codescene.io/api/1/projects/CSE
```

#### Example Response

```json
HTTP/1.1 200 OK
...
Content-Type: application/json;charset=UTF-8
Cache-Control: max-age=14400

{
  "id": "CSE",
  "idType": "ticket-id",
  "costUnit": {
    "type": "minutes"
  },
  "workTypes": ["Bug", "Feature", "Refactoring", "Documentation"],
  "items": [
    {
      "id": "15",
      "cost": 12,
      "types": [1, 0, 0, 0]
    },
    {
      "id": "21",
      "cost": 4,
      "types": [1, 0, 0, 0]
    },
    {
      "id": "181",
      "cost": 42,
      "types": [0, 1, 0, 1]
    }
  ]
}
```

### `GET /api/1/status`

Returns the status and basic information of the service. Should respond with
a HTTP status of 200 for signaling that it is available and functional, and
503 if it's unavailable or not functioning.

#### Example Request

```bash
curl -i -X GET -H 'Accept: application/json' -u 'user:pass' https://jira-integration.codescene.io/api/1/status
```

#### Example Response

```json
HTTP/1.1 200 OK
...
Content-Type: application/json;charset=UTF-8
Cache-Control: max-age=0

{
  "status": "ok",
  "name": "My Project Management System"
}
```

## Test/REPL Stuff

Adding/replacing a project with some test data:

``` clojure
> (def conn (codescene-enterprise-pm-jira.db/persistent-connection))
> (replace-project conn
    {:key "CSE"
     :cost-unit {:type "numeric"
                 :format {:singular "point" :plural "points"}}}
    [{:key "CSE-1" :cost 10 :work-types ["Bug" "Documentation"]}
     {:key "CSE-2" :cost 25 :work-types ["Feature" "Documentation"]}
     {:key "CSE-3" :cost 5 :work-types ["Bug"]}])
```

Getting the project back:

```clojure
> (get-project conn "CSE")
;=>
{:key "CSE",
 :cost-unit {:type "numeric", :singular "point", :plural "points"},
 :issues ({:cost 10, :key "CSE-1", :work-types #{"Documentation" "Bug"}}
          {:cost 25, :key "CSE-2", :work-types #{"Documentation" "Feature"}}
          {:cost 5, :key "CSE-3", :work-types #{"Bug"}})}
```

## Using Story Points in JIRA

When JIRA is configured to use Story Points as estimate for stories, 
epics, and possibly other issue types, the points will be added in a
custom field JIRA creates for this purpose when Story Points is configured.
The custom field will get a generated id. In order to be able to configure 
the PM integration service correctly, this custom field needs to be identified.
The following command (against the JIRA service) using `curl` and `jq`
will filter out the custom field for a project with the key `CSE2` 
(see [Atlassian Answers](https://answers.atlassian.com/questions/180944/custom-field-name-in-jira-json)):

```
$ curl -u 'jirauser:jirapwd' \
'https://jira.example.com/rest/api/latest/issue/createmeta?expand=projects.issuetypes.fields'\
|jq '.projects[]|select(.key=="CSE2")|.issuetypes[]|select(.name=="Story")|.fields|with_entries(select(.value.name=="Story Points"))'
{
  "customfield_10006": {
    "required": false,
    "schema": {
      "type": "number",
      "custom": "com.atlassian.jira.plugin.system.customfieldtypes:float",
      "customId": 10006
    },
    "name": "Story Points",
    "hasDefaultValue": false,
    "operations": [
      "set"
    ]
  }
}
```

You can verify that this is in fact the field with the Story Points. Say
that you already have a story `CSE2-257` with `Estimate: 4` filled in, then 
you can find the field name and verify the points with this command:

```
$ curl -u 'jirauser:jirapwd' \
https://jira.example.com/rest/api/latest/issue/CSE2-257
{
  ...
  "fields": {
    ...
    "timetracking": {},
    "customfield_10006": 4,
```

This field name can then be placed in the configuration file:

```yaml
    cost-unit:
      type: points
        format:
          singular: '%d point'
          plural: '%d points'
    cost-field: customfield_10006
```

When using Minutes instead of Points, the sync performs a search for the 
configured field name, usually `timeoriginalestimate`, having a non-empty value:

```
curl -u 'jirauser:jirapwd' \
'https://jira.example.com/rest/api/latest/search?jql=project=CSE2+and+timeoriginalestimate!=NULL'
```

Custom fields, however, cannot be searched like regular fields. Unfortunately,
it seems it's not possible to just use the complete field name, `customfield_10006`:

```
$ curl -u 'jirauser:jirapwd' \
'https://jira.example.com/rest/api/latest/search?jql=project=CSE2+and+customfield_10006!=NULL'
{
  "errorMessages": [
    "Field 'customfield_10006' does not exist or you do not have permission to view it."
  ],
  "errors": {}
}
```

Instead, there is a variant that uses `cf[ID]` (see [JIRA documentation](https://confluence.atlassian.com/jirasoftwarecloud/advanced-searching-fields-reference-764478339.html#Advancedsearching-fieldsreference-customCustomfield)), where `ID` is the id of the
custom field, in our case `10006`. Note that the brackets must be URL-encoded,
so `cf[10006]` turns into `cf%5B10006%5D`:

```
curl -u 'jirauser:jirapwd' \
'https://jira.example.com/rest/api/latest/search?jql=project=CSE2+and+cf%5B10006%5D!=NULL'|jq .
```

This means that the code for sync must detect whether a custom field is 
being used, extract the id, and use that in the query.

## License

Copyright © 2016 Empear
