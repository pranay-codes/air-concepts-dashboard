package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.web.ApiFailure;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** A remap or unknown historical origin needs review; never assign it to the current tenant by inference. */
final class TenantProvenance {
  private TenantProvenance() {}
  static void requireValid(JdbcTemplate jdbc, UUID entity) {
    String lock = """
        select c.entity_id from public.xero_entity_connections c
        join public.entities e on e.id=c.entity_id and e.archived_at is null
        join public.green_line_entity_settings s on s.entity_id=c.entity_id and s.enabled
        """ + (entity == null ? "" : " where c.entity_id=?") + " order by c.entity_id for share of c";
    if (entity == null) jdbc.queryForList(lock); else jdbc.queryForList(lock, entity);
    String sql = """
        select exists(select 1 from public.work_items w
          join public.entities e on e.id=w.entity_id and e.archived_at is null
          join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled
          left join public.xero_entity_connections c on c.entity_id=w.entity_id
          where (c.tenant_id is null or w.xero_tenant_id is distinct from c.tenant_id)
        """ + (entity == null ? ")" : " and w.entity_id=?)");
    Boolean mismatch = entity == null ? jdbc.queryForObject(sql, Boolean.class)
        : jdbc.queryForObject(sql, Boolean.class, entity);
    if (!Boolean.FALSE.equals(mismatch))
      throw new ApiFailure(HttpStatus.CONFLICT, "XERO_TENANT_CHANGED", "Work items need review after the Xero connection changed.");
  }
}
