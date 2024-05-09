create function hasura.get_event_logs(_trigger_name text)
returns table (
  model_id int,
  model_name text,
  model_version text,
  triggering_user text,
  delivered boolean,
  success boolean,
  tries int,
  created_at timestamp,
  next_retry_at timestamp,
  status int,
  error jsonb,
  error_message text,
  error_type text
)
stable
security invoker
language plpgsql as $$
begin
  return query (
    select
      (el.payload->'data'->'new'->>'id')::int as model_id,
      el.payload->'data'->'new'->>'name' as model_name,
      el.payload->'data'->'new'->>'version' as model_version,
      el.payload->'session_variables'->>'x-hasura-user-id' as triggering_user,
      el.delivered,
      eil.status is not distinct from 200 as success, -- is not distinct from to catch `null`
      el.tries,
      el.created_at,
      el.next_retry_at,
      eil.status,
      (eil.response -> 'data'->> 'message')::jsonb as error,
      (eil.response -> 'data'->> 'message')::jsonb->>'message' as error_message,
      (eil.response -> 'data'->> 'message')::jsonb->>'type' as error_type
      from hdb_catalog.event_log el
      join hdb_catalog.event_invocation_logs eil on el.id = eil.event_id
      where trigger_name = _trigger_name);
end;
$$;
comment on function hasura.get_event_logs(_trigger_name text) is e''
 'Get the logs for every run of a Hasura event with the specified trigger name.';

create view hasura.refresh_activity_type_logs as
  select * from hasura.get_event_logs('refreshActivityTypes');
comment on view hasura.refresh_activity_type_logs is e''
 'View containing logs for every run of the Hasura event `refreshActivityTypes`.';

create view hasura.refresh_model_parameter_logs as
  select * from hasura.get_event_logs('refreshModelParameters');
comment on view hasura.refresh_model_parameter_logs is e''
 'View containing logs for every run of the Hasura event `refreshModelParameters`.';

create view hasura.refresh_resource_type_logs as
  select * from hasura.get_event_logs('refreshResourceTypes');
comment on view hasura.refresh_resource_type_logs is e''
 'View containing logs for every run of the Hasura event `refreshResourceTypes`.';


