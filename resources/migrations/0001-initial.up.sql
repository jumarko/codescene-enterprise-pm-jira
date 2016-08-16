CREATE TABLE project_config
(
  project_key VARCHAR NOT NULL,
  cost_unit_type VARCHAR NOT NULL,
  cost_unit_format_singular VARCHAR,
  cost_unit_format_plural VARCHAR,
  PRIMARY KEY (project_key)
);

--;;

CREATE TABLE issues
(
  project_key VARCHAR,
  issue_key VARCHAR,
  cost INT NOT NULL DEFAULT 0,
  PRIMARY KEY (project_key, issue_key),
  FOREIGN KEY (project_key) REFERENCES project_config (project_key)
);

--;;

CREATE TABLE issues_work_types
(
  project_key VARCHAR,
  issue_key VARCHAR,
  type_name VARCHAR,
  PRIMARY KEY (project_key, issue_key, type_name),
  FOREIGN KEY (project_key) REFERENCES project_config (project_key),
  FOREIGN KEY (issue_key) REFERENCES issues (issue_key)
);
