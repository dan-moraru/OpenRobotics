-- V1__init.sql

-- UUID generation (Supabase supports pgcrypto)
create extension if not exists pgcrypto;

-- -------------------------
-- maps
-- -------------------------
create table if not exists maps (
  id uuid primary key default gen_random_uuid(),
  name varchar not null,
  width int not null,
  height int not null,
  tile_data jsonb not null,
  is_preset boolean not null default false,
  random_seed int,
  created_at timestamptz not null default now()
);

-- -------------------------
-- simulation_runs
-- -------------------------
create table if not exists simulation_runs (
  id uuid primary key default gen_random_uuid(),
  map_id uuid not null references maps(id) on delete restrict,

  robot_count int not null,
  coordination_policy varchar,
  robot_algorithms jsonb,
  workload_seed int,
  workload_settings jsonb,
  sim_settings jsonb,

  started_at timestamptz,
  finished_at timestamptz,
  status varchar
);

create index if not exists idx_simulation_runs_map_id
  on simulation_runs(map_id);

-- -------------------------
-- run_results (1:1 with simulation_runs)
-- PK is also FK => enforces one row per run
-- -------------------------
create table if not exists run_results (
  run_id uuid primary key references simulation_runs(id) on delete cascade,

  completion_time_ticks int,
  tasks_per_minute numeric,
  avg_delivery_time_ticks numeric,
  total_energy numeric,
  energy_per_task numeric,

  collisions int,
  near_misses int,
  deadlock_count int,
  battery_deaths int,

  fairness_gini numeric,
  extra_metrics jsonb
);

-- -------------------------
-- run_workload_tasks (many per run)
-- -------------------------
create table if not exists run_workload_tasks (
  id bigint generated always as identity primary key,
  run_id uuid not null references simulation_runs(id) on delete cascade,

  task_type varchar,
  priority int,

  created_tick int,
  assigned_tick int,
  completed_tick int,

  pickup_x int,
  pickup_y int,
  dropoff_x int,
  dropoff_y int,

  status varchar,
  assigned_robot_id int,

  details jsonb
);

create index if not exists idx_run_workload_tasks_run_id
  on run_workload_tasks(run_id);

create index if not exists idx_run_workload_tasks_run_robot
  on run_workload_tasks(run_id, assigned_robot_id);

-- -------------------------
-- sim_logs (many per run)
-- -------------------------
create table if not exists sim_logs (
  id bigint generated always as identity primary key,
  run_id uuid not null references simulation_runs(id) on delete cascade,

  tick int,
  robot_id int,
  event_type varchar,

  x int,
  y int,

  details jsonb
);

create index if not exists idx_sim_logs_run_tick
  on sim_logs(run_id, tick);

create index if not exists idx_sim_logs_run_robot
  on sim_logs(run_id, robot_id);

-- -------------------------
-- robot_run_stats (many per run; typically one per robot per run)
-- -------------------------
create table if not exists robot_run_stats (
  id bigint generated always as identity primary key,
  run_id uuid not null references simulation_runs(id) on delete cascade,

  robot_id int not null,
  nav_algorithm varchar,

  tasks_completed int,
  distance_traveled numeric,
  energy_used numeric,

  idle_ticks int,
  wait_ticks int,

  collisions int,
  near_misses int,
  deadlocks int
);

-- Enforce 1 stats row per robot per run (optional but usually correct)
create unique index if not exists uq_robot_run_stats_run_robot
  on robot_run_stats(run_id, robot_id);

create index if not exists idx_robot_run_stats_run_id
  on robot_run_stats(run_id);
