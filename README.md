# codescene-enterprise-pm-jira

A *Project Management* service which integrates JIRA with CodeScene Enterprise.

## Overview

This service:

- [ ] Periodically syncs issues from JIRA
- [ ] Stores issues in a local database
- [ ] Syncs only issues with cost values set
- [ ] Supported *types of work* can be configured
- [ ] Cost unit type can be configured
- [ ] JIRA Labels can be mapped to types of work in configuration

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein run
    PORT=3001 lein run
    CODESCENE_JIRA_CONFIG=/etc/codescene-jira.yml lein run

## Configuration

```yaml
sync:
  ... # todo
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
      type: {minutes|numeric}
      format: #optional
        singular: {format-string}
        plural: {format-string}
    cost-field: {jira-field-name}
    supported-work-types: [{jira-label}]
    ticket-id-pattern: {regex-pattern}
```

### Example Configuration

```yaml
sync:
  ... # todo
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
    ticket-id-pattern: CSE-(\d+)
  - key: DVP
    cost-unit:
      type: numeric
      format:
        singular: '%d point'
        plural: '%d points'
    cost-field: storypoints
    supported-work-types:
      - Bug
      - Feature
      - Refactoring
      - Documentation
    ticket-id-pattern: DVP-(\d+)
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
numbers, 0 or 1, corresponding to the work types in `"supportedWorkTypes"` of
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

## License

Copyright © 2016 Empear
