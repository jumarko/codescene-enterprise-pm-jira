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

    lein ring server-headless

**TEMPORARY:** Get a project's issues directly from JIRA, run:

    curl -u user:pass -X GET "http://jira-integration.codescene.io/rest/api/latest/search?jql=timeoriginalestimate!=EMPTY" -s | jq .issues[].fields.timeoriginalestimate

## Configuration

```yaml
sync:
  ... # todo
projects:
  - key: {jira-project-key}
    costUnit:
      type: {time|numeric}
      format: #optional
        singular: '{format-string}'
        plural: '{format-string}'
    costField: {jira-field-name}
    supportedWorkTypes: ['{jira-label}']
```

### Example Configuration

```yaml
sync:
  ... # todo
projects:
  - key: CSE
    costUnit:
      type: minutes
    costField: timeoriginalestimate
    supportedWorkTypes:
      - Bug
      - Feature
      - Refactoring
      - Documentation
  - key: DVP
    costUnit:
      type: numeric
      format:
        singular: '%d point'
        plural: '%d points'
    costField: storypoints
    supportedWorkTypes:
      - Bug
      - Feature
      - Refactoring
      - Documentation
```

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
  "costUnit": {
    "type": "minutes"
  },
  "supportedWorkTypes": ["Bug", "Feature", "Refactoring", "Documentation"],
  "items": [
    {
      "id": "CSE-15",
      "cost": 12,
      "types": [1, 0, 0, 0]
    },
    {
      "id": "CSE-21",
      "cost": 4,
      "types": [1, 0, 0, 0]
    },
    {
      "id": "CSE-181",
      "cost": 42,
      "types": [0, 1, 0, 1]
    }
  ]
}
```

## License

Copyright © 2016 Empear
