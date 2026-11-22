package com.airconcepts.dashboard.vwork;

import com.airconcepts.dashboard.web.ApiFailure;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The caller holds the staff and global assignment locks in a transaction. */
@Repository
public class VworkTemplatesRepository {
  public record Template(String templateRef, boolean isDefault) {}
  private final JdbcTemplate jdbc;
  public VworkTemplatesRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public void lockAssignments() { jdbc.execute("select pg_advisory_xact_lock(150023)"); }

  public List<Template> read(UUID entity) {
    return jdbc.query("select template_ref,is_default from public.vwork_entity_templates where entity_id=? order by template_ref",
        (row, index) -> new Template(row.getString("template_ref"), row.getBoolean("is_default")), entity);
  }

  public void replace(UUID entity, UUID actor, List<Template> desired) {
    var before = read(entity);
    for (var item : desired) {
      var owners = jdbc.query("select entity_id from public.vwork_entity_templates where template_ref=? for update",
          (row, index) -> row.getObject("entity_id", UUID.class), item.templateRef());
      if (!owners.isEmpty() && !entity.equals(owners.getFirst()))
        throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_TEMPLATE_IN_USE", "A template is already assigned to another entity.");
    }
    var ordered = desired.stream().sorted(java.util.Comparator.comparing(Template::templateRef)).toList();
    if (before.equals(ordered)) return;
    String previous = snapshot(entity);
    // Clear the old default first so the partial unique index remains valid throughout the change.
    jdbc.update("update public.vwork_entity_templates set is_default=false where entity_id=? and is_default", entity);
    Set<String> wanted = desired.stream().map(Template::templateRef).collect(Collectors.toSet());
    for (var old : before) if (!wanted.contains(old.templateRef()))
      jdbc.update("delete from public.vwork_entity_templates where entity_id=? and template_ref=?", entity, old.templateRef());
    for (var item : desired) {
      int changed = jdbc.update("""
          insert into public.vwork_entity_templates(template_ref,entity_id,is_default,created_by) values (?,?,?,?)
          on conflict (template_ref) do update set is_default=excluded.is_default
          where public.vwork_entity_templates.entity_id=excluded.entity_id
          """, item.templateRef(), entity, item.isDefault(), actor);
      if (changed != 1) throw new ApiFailure(HttpStatus.CONFLICT, "VWORK_TEMPLATE_IN_USE", "A template is already assigned to another entity.");
    }
    jdbc.update("""
        insert into public.work_item_events(entity_id,actor_id,source,event_type,before,after)
        values (?,?,'USER','SETTINGS_CHANGED',jsonb_build_object('templates',cast(? as jsonb)),
          jsonb_build_object('templates',cast(? as jsonb)))
        """, entity, actor, previous, snapshot(entity));
  }

  private String snapshot(UUID entity) {
    return jdbc.queryForObject("""
        select coalesce(jsonb_agg(jsonb_build_object('templateRef',template_ref,'isDefault',is_default)
          order by template_ref),'[]'::jsonb)::text from public.vwork_entity_templates where entity_id=?
        """, String.class, entity);
  }
}
