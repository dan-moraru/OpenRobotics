-- Convert robot identifier columns from int to uuid.

-- run_workload_tasks.assigned_robot_id
drop index if exists idx_run_workload_tasks_run_robot;
alter table run_workload_tasks add column assigned_robot_uuid uuid;
update run_workload_tasks
set assigned_robot_uuid = gen_random_uuid()
where assigned_robot_id is not null;
alter table run_workload_tasks drop column assigned_robot_id;
alter table run_workload_tasks rename column assigned_robot_uuid to assigned_robot_id;
create index if not exists idx_run_workload_tasks_run_robot
  on run_workload_tasks(run_id, assigned_robot_id);

-- sim_logs.robot_id
drop index if exists idx_sim_logs_run_robot;
alter table sim_logs add column robot_uuid uuid;
update sim_logs
set robot_uuid = gen_random_uuid()
where robot_id is not null;
alter table sim_logs drop column robot_id;
alter table sim_logs rename column robot_uuid to robot_id;
create index if not exists idx_sim_logs_run_robot
  on sim_logs(run_id, robot_id);

-- robot_run_stats.robot_id
drop index if exists uq_robot_run_stats_run_robot;
alter table robot_run_stats add column robot_uuid uuid;
update robot_run_stats
set robot_uuid = gen_random_uuid();
alter table robot_run_stats drop column robot_id;
alter table robot_run_stats rename column robot_uuid to robot_id;
alter table robot_run_stats alter column robot_id set not null;
create unique index if not exists uq_robot_run_stats_run_robot
  on robot_run_stats(run_id, robot_id);
