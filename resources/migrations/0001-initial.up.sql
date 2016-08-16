create table issues
(
  project_key VARCHAR,
  issue_key VARCHAR,
  cost INT,
  PRIMARY KEY (project_key, issue_key)
);

--;;

create table issues_work_types
(
  project_key VARCHAR,
  issue_key VARCHAR,
  type_name VARCHAR,
  PRIMARY KEY (project_key, issue_key, type_name),
  FOREIGN KEY (project_key, issue_key) REFERENCES issues
);
