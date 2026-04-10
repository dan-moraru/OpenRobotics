-- Convert robot identifier columns from int to uuid using a deterministic mapping.

create extension if not exists "uuid-ossp";

-- Build one deterministic mapping shared by all converted tables.
create temporary table robot_id_uuid_map as
select distinct
    ids.robot_id_int,
    uuid_generate_v5('6ba7b810-9dad-11d1-80b4-00c04fd430c8'::uuid, ids.robot_id_int::text) as robot_uuid
from (
    select assigned_robot_id as robot_id_int
    from run_workload_tasks
    where assigned_robot_id is not null
    union
    select robot_id as robot_id_int
    from sim_logs
    where robot_id is not null
    union
    select robot_id as robot_id_int
    from robot_run_stats
    where robot_id is not null
) ids;

-- run_workload_tasks.assigned_robot_id
alter table run_workload_tasks add column assigned_robot_uuid uuid;
update run_workload_tasks t
set assigned_robot_uuid = m.robot_uuid
from robot_id_uuid_map m
where t.assigned_robot_id = m.robot_id_int
  and t.assigned_robot_id is not null;
alter table run_workload_tasks drop column assigned_robot_id;
alter table run_workload_tasks rename column assigned_robot_uuid to assigned_robot_id;
create index if not exists idx_run_workload_tasks_run_robot
  on run_workload_tasks(run_id, assigned_robot_id);

-- sim_logs.robot_id
alter table sim_logs add column robot_uuid uuid;
update sim_logs t
set robot_uuid = m.robot_uuid
from robot_id_uuid_map m
where t.robot_id = m.robot_id_int
  and t.robot_id is not null;
alter table sim_logs drop column robot_id;
alter table sim_logs rename column robot_uuid to robot_id;
create index if not exists idx_sim_logs_run_robot
  on sim_logs(run_id, robot_id);

-- robot_run_stats.robot_id
alter table robot_run_stats add column robot_uuid uuid;
update robot_run_stats t
set robot_uuid = m.robot_uuid
from robot_id_uuid_map m
where t.robot_id = m.robot_id_int
  and t.robot_id is not null;
alter table robot_run_stats drop column robot_id;
alter table robot_run_stats rename column robot_uuid to robot_id;
alter table robot_run_stats alter column robot_id set not null;
create unique index if not exists uq_robot_run_stats_run_robot
  on robot_run_stats(run_id, robot_id);
