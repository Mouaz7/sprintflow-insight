-- Existing databases with the Java version of V8 need the one-time repair in ADR 0005.
do $$
begin
    if exists (
        select project_id from sprint
        where status = 'Active' and trashed_at is null
        group by project_id having count(*) > 1
    ) then
        raise exception 'Multiple active sprints exist. Resolve these conflicts before migrating; no sprint status was changed.';
    end if;
end
$$;

create unique index sprint_one_active_project_idx on sprint(project_id)
    where status = 'Active' and trashed_at is null;
