-- name: select-project
SELECT project_key,
       ticket_id_pattern,
       cost_unit_type,
       cost_unit_format_singular,
       cost_unit_format_plural FROM project_config
WHERE project_key = :project_key;

-- name: select-issues-in-project
SELECT issue_key, cost FROM issues
       WHERE project_key = :project_key;

-- name: select-work-types-in-project
SELECT issue_key, type_name FROM issues_work_types
       WHERE project_key = :project_key;

-- name: insert-project!
INSERT INTO project_config (project_key,
                            ticket_id_pattern,
                            cost_unit_type,
                            cost_unit_format_singular,
                            cost_unit_format_plural)
VALUES (:project_key,
        :ticket_id_pattern,
        :cost_unit_type,
        :cost_unit_format_singular,
        :cost_unit_format_plural);

-- name: insert-issue-into-project!
INSERT INTO issues (project_key, issue_key, cost)
VALUES (:project_key, :issue_key, :cost);

-- name: insert-issue-work-type-into-project!
INSERT INTO issues_work_types (project_key, issue_key, type_name)
VALUES (:project_key, :issue_key, :type_name);

-- name: delete-project!
DELETE FROM project_config
WHERE project_key = :project_key;

-- name: delete-issues-in-project!
DELETE FROM issues
WHERE project_key = :project_key;

-- name: delete-work-types-in-project!
DELETE FROM issues_work_types
WHERE project_key = :project_key;
