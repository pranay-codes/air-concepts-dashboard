package com.airconcepts.dashboard.xero;

import java.util.UUID;

/** Provider discovery result. Mapping endpoints accept connection ID, not arbitrary tenant ID. */
public record XeroTenant(UUID connectionId, UUID tenantId, String name) {}
