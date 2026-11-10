package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.entities.*;
import com.airconcepts.dashboard.security.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
public class ForecastAccess {
  public static final boolean DIRECTOR_CAN_WRITE=true;
  private final EntityService entities;
  public ForecastAccess(EntityService entities) {this.entities=entities;}
  public Entity requireRead(CurrentUser user,UUID entity) {user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);return entities.get(user,entity);}
  public Entity requireWrite(CurrentUser user,UUID entity) {
    if(DIRECTOR_CAN_WRITE) user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);else user.requireRole(Role.FINANCIAL_ADMIN);
    return entities.get(user,entity);
  }
}
