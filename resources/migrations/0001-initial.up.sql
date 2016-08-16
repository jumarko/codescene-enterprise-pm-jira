create table issues
(
  project_key VARCHAR,
  key VARCHAR,
  cost INT,
  type_of_work INT,
  PRIMARY KEY (project_key, key)
);

--;;

create table issues_work_types
(
  project_key VARCHAR,
  issue_key INT,
  type_name VARCHAR,
  PRIMARY KEY (project_key, issue_key, type_name),
  FOREIGN KEY (project_key, issue_key) REFERENCES issues
);
