-- issues

ALTER TABLE issues ALTER COLUMN project_key SET NOT NULL;

--;;

ALTER TABLE issues ALTER COLUMN key SET NOT NULL;

--;;

ALTER TABLE issues ALTER COLUMN cost SET NOT NULL;

--;;

ALTER TABLE issues ALTER COLUMN cost SET DEFAULT 0;

--;;

-- issues-work-types

ALTER TABLE issues_work_types ALTER COLUMN project_key SET NOT NULL;

--;;

ALTER TABLE issues_work_types ALTER COLUMN issue_key SET NOT NULL;

--;;

ALTER TABLE issues_work_types ALTER COLUMN type_name SET NOT NULL;

--;;

ALTER TABLE issues_work_types ALTER COLUMN type_name SET DEFAULT '';
