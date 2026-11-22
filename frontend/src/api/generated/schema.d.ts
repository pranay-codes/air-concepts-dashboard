export interface paths {
    "/api/v1/xero/attempts/{attemptId}/connection": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Confirm a freshly discovered organisation for the attempt's entity */
        put: operations["confirmXeroConnection"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/vwork-connection": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read vWork connection status */
        get: operations["getVworkConnection"];
        /** Connect or replace the vWork key (Director only) */
        put: operations["connectVwork"];
        post?: never;
        /** Disconnect vWork (Director only) */
        delete: operations["disconnectVwork"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/vwork-templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read an entity's vWork template assignments */
        get: operations["listEntityVworkTemplates"];
        /** Replace an entity's vWork templates (Director only) */
        put: operations["updateEntityVworkTemplates"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/invoices/{invoiceId}/assignment": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Assign or unassign a verified sales invoice; Director or Financial Admin only */
        put: operations["assignSalesInvoice"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/green-line/settings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read workflow status and Director settings for an entity */
        get: operations["getGreenLineSettings"];
        /** Update an entity's workflow settings (Director only) */
        put: operations["updateGreenLineSettings"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/transactions/{source}/{sourceId}/note": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["saveForecastTransactionNote"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/periods/{month}/opening-balance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getForecastOpeningBalance"];
        put: operations["saveForecastOpeningBalance"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/documents/{id}/splits": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["splitForecastDocument"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/documents/{id}/adjustment": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["adjustForecastDocument"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List staff including inactive accounts (Director only) */
        get: operations["listUsers"];
        put?: never;
        /** Provision a registered staff account (Director only) */
        post: operations["createUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List every configured entity accessible to active staff */
        get: operations["listEntities"];
        put?: never;
        /** Create a dashboard entity (Director only) */
        post: operations["createEntity"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/xero/authorization": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Start a Director-owned Xero authorisation attempt */
        post: operations["startXeroAuthorization"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/vwork-job": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Create or link a vWork job */
        post: operations["createVworkJob"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/operations/{operationId}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Retry a failed vWork job creation */
        post: operations["retryVworkOperation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/operations/{operationId}/lookup": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Look up an unconfirmed vWork job */
        post: operations["lookupVworkOperation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/no-invoice-needed": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Close a work item that needs no invoice */
        post: operations["closeWorkItemNoInvoice"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/mark-sent": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Confirm that an authorised invoice was sent */
        post: operations["markInvoiceSent"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/invoice": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Prepare a draft invoice for the job */
        post: operations["prepareInvoice"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/close-lost": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Close a quoted work item as lost */
        post: operations["closeWorkItemLost"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/green-line/sync": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read workflow sync status */
        get: operations["getGreenLineSync"];
        put?: never;
        /** Refresh workflow quotes */
        post: operations["refreshGreenLine"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/sync": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read the last ten forecast sync runs */
        get: operations["getForecastSyncHistory"];
        put?: never;
        /** Reconcile the entity forecast from its Xero organisation */
        post: operations["syncForecast"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/staffing": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listForecastStaffing"];
        put?: never;
        post: operations["createForecastStaffing"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/staffing/{id}/deactivate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["deactivateForecastStaffing"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/months/{month}/generate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["generateForecastMonth"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/manual-items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listForecastManualItems"];
        put?: never;
        post: operations["createForecastManualItem"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/manual-items/{id}/deactivate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["deactivateForecastManualItem"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["exportForecast"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/documents/{id}/conflict": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["resolveForecastConflict"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/archive": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Archive a dashboard entity (Director only). Records and the Xero grant are kept. */
        post: operations["archiveEntity"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/users/{userId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** View a dashboard staff account (Director only) */
        get: operations["getUser"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Change a staff name, role or active status (Director only) */
        patch: operations["updateUser"];
        trace?: never;
    };
    "/api/v1/entities/{entityId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** View a configured entity by its stable dashboard ID */
        get: operations["getEntity"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Rename a dashboard entity (Director only) */
        patch: operations["renameEntity"];
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read an entity's work item and timeline */
        get: operations["getWorkItem"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Update the work item's follow-up date, notes and owner */
        patch: operations["updateWorkItem"];
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/staffing/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["updateForecastStaffing"];
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/manual-items/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["updateForecastManualItem"];
        trace?: never;
    };
    "/api/v1/xero/callback": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Receive a single-use Xero OAuth callback and redirect without credentials */
        get: operations["xeroCallback"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/xero/attempts/{attemptId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read the current Director's result and authorised organisation choices */
        get: operations["getXeroAuthorizationAttempt"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/work-items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List work items with entity-scoped filters and pagination */
        get: operations["listWorkItems"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/work-items/my-work": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read the current user's work and attention queues */
        get: operations["getMyWork"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/staff": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List active assignment choices (Director or Financial Admin) */
        get: operations["listAssignableStaff"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read the current active staff identity and server-side role */
        get: operations["getCurrentUser"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/forecast/combined": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getCombinedForecast"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/xero": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read safe connection metadata for the selected entity */
        get: operations["getXeroConnection"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/job-draft": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Prepare a price-free vWork job draft */
        get: operations["getCreateJobDraft"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/work-items/{itemId}/invoice-draft": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Review invoice details from the Xero quote */
        get: operations["reviewInvoiceDraft"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/invoices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read a live filtered Xero invoice page; supplier bills require Director or Financial Admin */
        get: operations["listInvoices"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/invoices/{invoiceId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read a live Xero invoice or supplier bill, including lines and payments */
        get: operations["getInvoice"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/invoices/{invoiceId}/attachments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Read attachment metadata after verifying the entity and document type */
        get: operations["listInvoiceAttachments"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/invoices/{invoiceId}/attachments/{attachmentId}/file": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Download a verified bounded file; safe PDF/raster content may be requested inline */
        get: operations["downloadInvoiceAttachment"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getForecast"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/months/{month}/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getForecastMonthItems"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/documents": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listForecastDocuments"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/documents/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getForecastDocument"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/entities/{entityId}/forecast/audit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getForecastAudit"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/hello": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["hello"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        SelectConnection: {
            /** Format: uuid */
            connectionId: string;
        };
        XeroConnection: {
            /** Format: uuid */
            entityId: string;
            /** @enum {string} */
            status: "CONNECTED" | "NOT_CONNECTED" | "NEEDS_RECONNECTION";
            organisationName?: string;
            maskedTenantId?: string;
            connectedBy?: string;
            /** Format: date-time */
            lastConfirmedAt?: string;
            /** Format: date-time */
            lastCheckedAt?: string;
            errorCode?: string;
            /** @enum {string} */
            quoteAccess?: "NOT_ENABLED" | "UNKNOWN" | "READY" | "RECONNECT_REQUIRED";
        };
        VworkConnectionRequest: {
            /** @description vWork API key; write only */
            apiKey: string;
        };
        /** @description All active staff see status; only Directors receive connection metadata. Credentials are never returned. */
        VworkConnection: {
            status: string;
            /** Format: uuid */
            configuredBy?: string;
            /** Format: date-time */
            configuredAt?: string;
            /** Format: date-time */
            verifiedAt?: string;
            errorCode?: string;
        };
        EntityVworkTemplateInput: {
            templateRef: string;
            isDefault: boolean;
        };
        EntityVworkTemplatesRequest: {
            templates: components["schemas"]["EntityVworkTemplateInput"][];
        };
        EntityVworkTemplate: {
            templateRef: string;
            isDefault: boolean;
        };
        AssignInvoice: {
            /**
             * Format: uuid
             * @description Active staff ID; omit or use null to unassign
             */
            assigneeId?: string | null;
        };
        InvoiceOwner: {
            /** Format: uuid */
            id: string;
            displayName: string;
            active: boolean;
        };
        SalesAssignment: {
            /** Format: uuid */
            invoiceId: string;
            owner?: components["schemas"]["InvoiceOwner"];
        };
        SettingsRequest: {
            enabled: boolean;
            /** Format: uuid */
            defaultOwnerId?: string;
            /** Format: uuid */
            serviceManagerId?: string;
            /** Format: int32 */
            lateThresholdWorkingDays: number;
            /** Format: int32 */
            backfillDays: number;
        };
        /** @description All active staff see entity and enabled status; only Directors receive the other settings. */
        GreenLineSettings: {
            /** Format: uuid */
            entityId: string;
            enabled: boolean;
            /** Format: uuid */
            defaultOwnerId?: string;
            /** Format: uuid */
            serviceManagerId?: string;
            /** Format: int32 */
            lateThresholdWorkingDays?: number;
            /** Format: int32 */
            backfillDays?: number;
        };
        NoteRequest: {
            note?: string;
        };
        TransactionNote: {
            source?: string;
            /** Format: uuid */
            sourceId?: string;
            note?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        OpeningRequest: {
            /** Format: decimal */
            openingBalance?: string;
            source?: string;
        };
        OpeningBalance: {
            /** Format: decimal */
            openingBalance?: string;
            source?: string;
        };
        Split: {
            /** Format: int32 */
            sequence?: number;
            /** Format: date */
            expectedDate?: string;
            /** Format: decimal */
            amount?: string;
        };
        SplitsRequest: {
            lines?: components["schemas"]["Split"][];
        };
        AdjustmentState: {
            /** Format: uuid */
            documentId?: string;
            /** Format: date */
            expectedDateOverride?: string;
            excluded?: boolean;
            exclusionReason?: string;
            /** Format: date-time */
            dateConflictSince?: string;
            /** Format: date */
            previousXeroDate?: string;
            /** Format: date */
            newXeroDate?: string;
            lines?: components["schemas"]["Split"][];
        };
        AdjustmentChange: {
            /** Format: date */
            expectedDateOverride?: string;
            excluded?: boolean;
            exclusionReason?: string;
        };
        CreateStaff: {
            /** Format: email */
            email: string;
            displayName: string;
            /** @enum {string} */
            role: "DIRECTOR" | "FINANCIAL_ADMIN" | "STANDARD_USER";
        };
        StaffMember: {
            /** Format: uuid */
            id: string;
            email: string;
            displayName: string;
            /** @enum {string} */
            role: "DIRECTOR" | "FINANCIAL_ADMIN" | "STANDARD_USER";
            active: boolean;
        };
        CreateEntity: {
            displayName: string;
        };
        Entity: {
            /** Format: uuid */
            id: string;
            displayName: string;
        };
        XeroAuthorizationStart: {
            /** Format: uuid */
            attemptId: string;
            authorizationUrl: string;
            /** Format: date-time */
            expiresAt: string;
        };
        CreateJobRequest: {
            /** Format: int32 */
            version: number;
            reference: string;
            templateId: string;
            description: string;
            customer: string;
            contactPerson: string;
            phone: string;
            siteAddress: string;
            notes?: string;
            scope: components["schemas"]["CreateJobScopeLine"][];
        };
        CreateJobScopeLine: {
            description: string;
            quantity: string;
        };
        CreateJobResult: {
            /** Format: uuid */
            operationId: string;
            /** @enum {string} */
            outcome: "CREATED" | "LINKED_EXISTING" | "NOT_FOUND";
        };
        LookupResult: {
            /** @enum {string} */
            outcome?: "CREATED" | "LINKED_EXISTING" | "NOT_FOUND";
        };
        ReasonRequest: {
            /** Format: int32 */
            version: number;
            reason: string;
        };
        Detail: {
            summary?: components["schemas"]["Summary"];
            notes?: string;
            closingNote?: string;
            vwork?: components["schemas"]["VworkPanel"];
            invoice?: components["schemas"]["InvoicePanel"];
            links?: components["schemas"]["Links"];
            events?: components["schemas"]["EventView"][];
            operations?: components["schemas"]["OperationView"][];
            allowedActions?: ("EDIT_NOTES" | "CLOSE_LOST" | "CREATE_JOB" | "PREPARE_INVOICE" | "NO_INVOICE_NEEDED" | "MARK_SENT")[];
        };
        EventView: {
            /** Format: int64 */
            id?: number;
            /** Format: date-time */
            at?: string;
            actor?: components["schemas"]["StaffRef"];
            source?: string;
            eventType?: string;
            fromStage?: string;
            toStage?: string;
            /** Format: uuid */
            fromOwnerId?: string;
            /** Format: uuid */
            toOwnerId?: string;
            before?: string;
            after?: string;
        };
        InvoicePanel: {
            /** Format: uuid */
            invoiceId?: string;
            invoiceNumber?: string;
            status?: string;
            sent?: boolean;
            total?: string;
            amountDue?: string;
            /** Format: date */
            dueDate?: string;
        };
        Links: {
            xeroQuoteUrl?: string;
            xeroInvoiceUrl?: string;
            vworkJobUrl?: string;
        };
        OperationView: {
            /** Format: uuid */
            id?: string;
            /** @enum {string} */
            kind?: "VWORK_CREATE_JOB" | "XERO_CREATE_INVOICE" | "XERO_ATTACH_REPORT";
            /** @enum {string} */
            status?: "PENDING" | "UNKNOWN" | "FAILED" | "SUCCEEDED";
            /** @enum {string} */
            outcome?: "CREATED" | "LINKED_EXISTING" | "NOT_FOUND";
            /** Format: date-time */
            requestedAt?: string;
            allowLookup?: boolean;
            allowRetry?: boolean;
        };
        StaffRef: {
            /** Format: uuid */
            id?: string;
            displayName?: string;
        };
        Summary: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            entityId?: string;
            entityName?: string;
            /** Format: uuid */
            xeroQuoteId?: string;
            quoteNumber?: string;
            jobId?: string;
            customerName?: string;
            xeroQuoteStatus?: string;
            quoteTotal?: string;
            currency?: string;
            /** @enum {string} */
            stage?: "QUOTED" | "TO_SCHEDULE" | "SCHEDULED" | "TO_INVOICE" | "INVOICE_DRAFTED" | "INVOICED" | "CLOSED";
            closeReason?: string;
            owner?: components["schemas"]["StaffRef"];
            /** Format: date */
            followUpDate?: string;
            /** Format: date-time */
            stageEnteredAt?: string;
            /** Format: int64 */
            daysInStage?: number;
            late?: boolean;
            nextAction?: string;
            flags?: string[];
            attention?: string[];
            /** Format: int32 */
            version?: number;
        };
        VworkPanel: {
            jobId?: string;
            jobNumber?: string;
            status?: string;
            technician?: string;
            /** Format: date-time */
            scheduledStart?: string;
            /** Format: date-time */
            completedAt?: string;
            technicianNotes?: string;
            reportStatus?: string;
            searchState?: string;
        };
        VersionRequest: {
            /** Format: int32 */
            version: number;
        };
        PrepareInvoiceRequest: {
            /** Format: int32 */
            version: number;
            fingerprint: string;
        };
        PrepareInvoiceResult: {
            /** Format: uuid */
            operationId: string;
            /** @enum {string} */
            outcome: "CREATED" | "LINKED_EXISTING" | "NOT_FOUND";
        };
        GreenLineSyncRun: {
            /** Format: uuid */
            id: string;
            status: string;
            /** Format: date-time */
            startedAt: string;
            /** Format: date-time */
            finishedAt?: string;
            errorCode?: string;
        };
        GreenLineSyncStatus: {
            /** Format: uuid */
            entityId: string;
            xero: components["schemas"]["GreenLineSystemSyncStatus"];
            vwork: components["schemas"]["GreenLineSystemSyncStatus"];
        };
        GreenLineSystemSyncStatus: {
            latest?: components["schemas"]["GreenLineSyncRun"];
            lastSuccess?: components["schemas"]["GreenLineSyncRun"];
            lastFailure?: components["schemas"]["GreenLineSyncRun"];
        };
        SyncRun: {
            /** Format: uuid */
            id?: string;
            trigger?: string;
            /** Format: date-time */
            startedAt?: string;
            /** Format: date-time */
            finishedAt?: string;
            status?: string;
            /** Format: int32 */
            documentsSeen?: number;
            /** Format: int32 */
            documentsInserted?: number;
            /** Format: int32 */
            documentsUpdated?: number;
            /** Format: int32 */
            documentsSettled?: number;
            /** Format: int32 */
            conflictsFlagged?: number;
            errorCode?: string;
            /** Format: int32 */
            upstreamStatus?: number;
            /** Format: int32 */
            dayLimitRemaining?: number;
            /** Format: int32 */
            minuteLimitRemaining?: number;
            /** Format: int32 */
            appMinuteLimitRemaining?: number;
        };
        StaffingRequest: {
            label?: string;
            /** Format: decimal */
            monthlyCost?: string;
            /** Format: int32 */
            payDayOfMonth?: number;
            startMonth?: string;
            endMonth?: string | null;
            active?: boolean;
        };
        StaffingLine: {
            /** Format: uuid */
            id?: string;
            label?: string;
            /** Format: decimal */
            monthlyCost?: string;
            /** Format: int32 */
            payDayOfMonth?: number;
            startMonth?: string;
            endMonth?: string;
            active?: boolean;
        };
        GenerationResult: {
            month?: string;
            state?: string;
            generatable?: boolean;
            /** Format: int32 */
            generationCount?: number;
            /** Format: int32 */
            addedLines?: number;
            addedReceipts?: number;
            addedPayments?: number;
        };
        ManualRequest: {
            direction?: string;
            category?: string;
            description?: string;
            /** Format: decimal */
            amount?: string;
            recurrence?: string;
            /** Format: int32 */
            dayOfMonth?: number;
            startMonth?: string;
            endMonth?: string | null;
            active?: boolean;
        };
        ManualItem: {
            /** Format: uuid */
            id?: string;
            direction?: string;
            category?: string;
            description?: string;
            /** Format: decimal */
            amount?: string;
            recurrence?: string;
            /** Format: int32 */
            dayOfMonth?: number;
            startMonth?: string;
            endMonth?: string;
            active?: boolean;
        };
        ConflictRequest: {
            resolution?: string;
        };
        UpdateStaff: {
            displayName: string;
            /** @enum {string} */
            role: "DIRECTOR" | "FINANCIAL_ADMIN" | "STANDARD_USER";
            active: boolean;
        };
        RenameEntity: {
            displayName: string;
        };
        UpdateRequest: {
            /** Format: int32 */
            version: number;
            /** Format: date */
            followUpDate?: string;
            notes?: string;
            clearFollowUp?: boolean;
            /** Format: uuid */
            ownerId?: string;
            clearOwner?: boolean;
        };
        XeroAuthorizationAttempt: {
            /** Format: uuid */
            id: string;
            /** Format: uuid */
            entityId: string;
            /** @enum {string} */
            state: "AWAITING_CALLBACK" | "PROCESSING" | "READY" | "FAILED" | "COMPLETED";
            errorCode?: string;
            /** Format: int32 */
            upstreamStatus?: number;
            choices: components["schemas"]["XeroTenantChoice"][];
        };
        XeroTenantChoice: {
            /** Format: uuid */
            connectionId: string;
            name: string;
        };
        WorkItemPage: {
            items?: components["schemas"]["Summary"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            pageSize?: number;
            /** Format: int64 */
            total?: number;
            /** Format: int64 */
            totalPages?: number;
        };
        MyWork: {
            needsAction?: components["schemas"]["Summary"][];
            waiting?: components["schemas"]["Summary"][];
            needsAttention?: components["schemas"]["Summary"][];
            /** Format: date-time */
            generatedAt?: string;
            truncated?: boolean;
        };
        StaffOption: {
            /** Format: uuid */
            id: string;
            displayName: string;
        };
        MeResponse: {
            subject: string;
            /** @enum {string} */
            role: "DIRECTOR" | "FINANCIAL_ADMIN" | "STANDARD_USER";
            displayName: string;
        };
        CombinedView: {
            financialYearStart?: string;
            financialYearLabel?: string;
            months?: components["schemas"]["MonthRow"][];
            entities?: components["schemas"]["EntitySummary"][];
            complete?: boolean;
        };
        EntitySummary: {
            /** Format: uuid */
            entityId?: string;
            entityName?: string;
            months?: components["schemas"]["MonthRow"][];
            exceptions?: string[];
            complete?: boolean;
        };
        MonthRow: {
            month: string;
            /** Format: decimal */
            opening?: string | null;
            /** Format: decimal */
            receipts?: string | null;
            /** Format: decimal */
            payments?: string | null;
            /** Format: decimal */
            net?: string | null;
            /** Format: decimal */
            closing?: string | null;
            /** Format: int32 */
            receiptCount: number;
            /** Format: int32 */
            paymentCount: number;
            containsOverdue: boolean;
            /** @enum {string} */
            state: "GENERATED" | "PREVIEW" | "NOT_GENERATED";
            generatable: boolean;
            /** Format: date-time */
            generatedAt?: string;
            /** Format: int32 */
            pendingNewItems: number;
            /** @enum {string|null} */
            openingSource?: "MANUAL" | "OVERRIDE" | "CARRIED_FORWARD" | null;
        };
        CreateJobDraft: {
            /** Format: uuid */
            entityId: string;
            /** Format: uuid */
            itemId: string;
            /** Format: int32 */
            version: number;
            reference?: string;
            quoteNumber: string;
            customer?: string;
            contactPerson?: string;
            phone?: string;
            siteAddress?: string;
            scope: components["schemas"]["ScopeLine"][];
            templates: components["schemas"]["Template"][];
            defaultTemplate?: string;
            missingFields: string[];
        };
        ScopeLine: {
            description?: string;
            quantity?: string;
        };
        Template: {
            templateRef?: string;
            isDefault?: boolean;
        };
        InvoiceDraftReview: {
            /** Format: uuid */
            entityId: string;
            /** Format: uuid */
            itemId: string;
            /** Format: int32 */
            version: number;
            quoteNumber: string;
            organisationName: string;
            customer: string;
            reference: string;
            /** Format: int32 */
            lineCount: number;
            total: string;
            currency: string;
            fingerprint: string;
        };
        InvoicePage: {
            items: components["schemas"]["InvoiceSummary"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            pageSize: number;
            /** Format: int32 */
            pageCount: number;
            /** Format: int64 */
            itemCount: number;
        };
        InvoiceSummary: {
            /** Format: uuid */
            id: string;
            /** @enum {string} */
            type: "ACCREC" | "ACCPAY";
            number?: string;
            reference?: string;
            contact: string;
            /** Format: date */
            date: string;
            /** Format: date */
            dueDate?: string;
            /** Format: decimal */
            total?: string;
            /** Format: decimal */
            amountDue?: string;
            currency?: string;
            /** @enum {string} */
            status: "DRAFT" | "SUBMITTED" | "AUTHORISED" | "PAID" | "VOIDED" | "DELETED";
            overdue: boolean;
            owner?: components["schemas"]["InvoiceOwner"];
        };
        InvoiceDetail: {
            invoice: components["schemas"]["InvoiceSummary"];
            /** Format: decimal */
            subtotal?: string;
            /** Format: decimal */
            tax?: string;
            /** Format: decimal */
            amountPaid?: string;
            /** Format: decimal */
            amountCredited?: string;
            lines: components["schemas"]["InvoiceLine"][];
            payments: components["schemas"]["InvoicePayment"][];
            /** @enum {string} */
            taxBasis?: "EXCLUSIVE" | "INCLUSIVE" | "NO_TAX";
        };
        InvoiceLine: {
            description?: string;
            /** Format: decimal */
            quantity?: string;
            /** Format: decimal */
            unitAmount?: string;
            /** Format: decimal */
            lineAmount?: string;
            /** Format: decimal */
            taxAmount?: string;
        };
        InvoicePayment: {
            /** Format: uuid */
            id?: string;
            /** Format: date */
            date?: string;
            /** Format: decimal */
            amount?: string;
            reference?: string;
        };
        InvoiceAttachment: {
            /** Format: uuid */
            id: string;
            fileName: string;
            contentType: string;
            /** Format: int64 */
            size: number;
            previewable: boolean;
        };
        BeyondWindow: {
            /** Format: decimal */
            receipts?: string;
            /** Format: decimal */
            payments?: string;
        };
        Exclusion: {
            /** Format: uuid */
            documentId?: string;
            number?: string;
            reason?: string;
        };
        ForecastException: {
            kind?: string;
            /** Format: uuid */
            documentId?: string;
            message?: string;
        };
        ForecastView: {
            /** Format: uuid */
            entityId: string;
            entityName: string;
            financialYearStart: string;
            financialYearLabel: string;
            months: components["schemas"]["MonthRow"][];
            totals: components["schemas"]["Totals"];
            exceptions: components["schemas"]["ForecastException"][];
            beyondWindow: components["schemas"]["BeyondWindow"];
            exclusions: components["schemas"]["Exclusion"][];
            /** Format: date-time */
            lastSuccessfulSyncAt?: string;
            lastRun?: components["schemas"]["LastRun"];
            syncStale: boolean;
            openingBalanceSource?: string;
        };
        LastRun: {
            status?: string;
            /** Format: date-time */
            finishedAt?: string;
            errorCode?: string;
        };
        Totals: {
            /** Format: decimal */
            receipts?: string | null;
            /** Format: decimal */
            payments?: string | null;
            /** Format: decimal */
            closing?: string | null;
        };
        Contribution: {
            /** Format: uuid */
            sourceId?: string;
            source?: string;
            direction?: string;
            category?: string;
            number?: string;
            label?: string;
            /** Format: date */
            date?: string;
            bucket?: string;
            /** Format: decimal */
            amount?: string;
            /** @enum {string} */
            rule?: "SPLIT" | "OVERRIDE" | "XERO_EXPECTED_DATE" | "DUE_DATE" | "ISSUE_DATE" | "MANUAL" | "STAFFING";
            overdue?: boolean;
            /** Format: int32 */
            splitSequence?: number;
            /** Format: int32 */
            splitCount?: number;
            /** Format: date */
            issueDate?: string;
            /** Format: date */
            dueDate?: string;
        };
        MonthItems: {
            month: string;
            state: string;
            generatable: boolean;
            items: components["schemas"]["Contribution"][];
            notes: components["schemas"]["TransactionNote"][];
        };
        DocumentPage: {
            items?: components["schemas"]["StoredDocument"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            pageSize?: number;
            /** Format: int64 */
            itemCount?: number;
        };
        StoredDocument: {
            /** Format: uuid */
            id?: string;
            type?: string;
            number?: string;
            contact?: string;
            status?: string;
            outstanding?: boolean;
            /** Format: date */
            dueDate?: string;
            /** Format: date */
            xeroExpectedDate?: string;
            /** Format: decimal */
            amountDue?: string;
            adjustment?: components["schemas"]["AdjustmentState"];
        };
        AuditPage: {
            items?: components["schemas"]["AuditRow"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            pageSize?: number;
            /** Format: int64 */
            itemCount?: number;
        };
        AuditRow: {
            /** Format: int64 */
            id?: number;
            /** Format: uuid */
            actorId?: string;
            /** Format: date-time */
            at?: string;
            recordTable?: string;
            recordKey?: string;
            action?: string;
            before?: {
                [key: string]: unknown;
            };
            after?: {
                [key: string]: unknown;
            };
        };
        HelloResponse: {
            message?: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    confirmXeroConnection: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                attemptId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SelectConnection"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["XeroConnection"];
                };
            };
        };
    };
    getVworkConnection: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VworkConnection"];
                };
            };
        };
    };
    connectVwork: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["VworkConnectionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VworkConnection"];
                };
            };
        };
    };
    disconnectVwork: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["VworkConnection"];
                };
            };
        };
    };
    listEntityVworkTemplates: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EntityVworkTemplate"][];
                };
            };
        };
    };
    updateEntityVworkTemplates: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["EntityVworkTemplatesRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EntityVworkTemplate"][];
                };
            };
        };
    };
    assignSalesInvoice: {
        parameters: {
            query?: {
                type?: "ACCREC" | "ACCPAY";
            };
            header?: never;
            path: {
                entityId: string;
                invoiceId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AssignInvoice"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SalesAssignment"];
                };
            };
        };
    };
    getGreenLineSettings: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GreenLineSettings"];
                };
            };
        };
    };
    updateGreenLineSettings: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SettingsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GreenLineSettings"];
                };
            };
        };
    };
    saveForecastTransactionNote: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                source: string;
                sourceId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NoteRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TransactionNote"];
                };
            };
        };
    };
    getForecastOpeningBalance: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                month: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OpeningBalance"];
                };
            };
        };
    };
    saveForecastOpeningBalance: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                month: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OpeningRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OpeningBalance"];
                };
            };
        };
    };
    splitForecastDocument: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SplitsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdjustmentState"];
                };
            };
        };
    };
    adjustForecastDocument: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AdjustmentChange"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdjustmentState"];
                };
            };
        };
    };
    listUsers: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StaffMember"][];
                };
            };
        };
    };
    createUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateStaff"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StaffMember"];
                };
            };
        };
    };
    listEntities: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Entity"][];
                };
            };
        };
    };
    createEntity: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateEntity"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Entity"];
                };
            };
        };
    };
    startXeroAuthorization: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["XeroAuthorizationStart"];
                };
            };
        };
    };
    createVworkJob: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateJobRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateJobResult"];
                };
            };
        };
    };
    retryVworkOperation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
                operationId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateJobRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateJobResult"];
                };
            };
        };
    };
    lookupVworkOperation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
                operationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LookupResult"];
                };
            };
        };
    };
    closeWorkItemNoInvoice: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReasonRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Detail"];
                };
            };
        };
    };
    markInvoiceSent: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["VersionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Detail"];
                };
            };
        };
    };
    prepareInvoice: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PrepareInvoiceRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PrepareInvoiceResult"];
                };
            };
        };
    };
    closeWorkItemLost: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReasonRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Detail"];
                };
            };
        };
    };
    getGreenLineSync: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["GreenLineSyncStatus"];
                };
            };
        };
    };
    refreshGreenLine: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["GreenLineSyncStatus"];
                };
            };
        };
    };
    getForecastSyncHistory: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SyncRun"][];
                };
            };
        };
    };
    syncForecast: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SyncRun"];
                };
            };
        };
    };
    listForecastStaffing: {
        parameters: {
            query?: {
                includeInactive?: boolean;
            };
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StaffingLine"][];
                };
            };
        };
    };
    createForecastStaffing: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StaffingRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StaffingLine"];
                };
            };
        };
    };
    deactivateForecastStaffing: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StaffingLine"];
                };
            };
        };
    };
    generateForecastMonth: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                month: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["GenerationResult"];
                };
            };
        };
    };
    listForecastManualItems: {
        parameters: {
            query?: {
                includeInactive?: boolean;
            };
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ManualItem"][];
                };
            };
        };
    };
    createForecastManualItem: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ManualRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ManualItem"];
                };
            };
        };
    };
    deactivateForecastManualItem: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ManualItem"];
                };
            };
        };
    };
    exportForecast: {
        parameters: {
            query?: {
                financialYearStart?: string;
            };
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    /** Format: binary */
                    file: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": string;
                };
            };
        };
    };
    resolveForecastConflict: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConflictRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdjustmentState"];
                };
            };
        };
    };
    archiveEntity: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    getUser: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StaffMember"];
                };
            };
        };
    };
    updateUser: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateStaff"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StaffMember"];
                };
            };
        };
    };
    getEntity: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Entity"];
                };
            };
        };
    };
    renameEntity: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RenameEntity"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Entity"];
                };
            };
        };
    };
    getWorkItem: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Detail"];
                };
            };
        };
    };
    updateWorkItem: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Detail"];
                };
            };
        };
    };
    updateForecastStaffing: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StaffingRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StaffingLine"];
                };
            };
        };
    };
    updateForecastManualItem: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ManualRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ManualItem"];
                };
            };
        };
    };
    xeroCallback: {
        parameters: {
            query?: {
                state?: string;
                code?: string;
                error?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description See Other */
            303: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    getXeroAuthorizationAttempt: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                attemptId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["XeroAuthorizationAttempt"];
                };
            };
        };
    };
    listWorkItems: {
        parameters: {
            query?: {
                entityId?: string;
                stage?: "QUOTED" | "TO_SCHEDULE" | "SCHEDULED" | "TO_INVOICE" | "INVOICE_DRAFTED" | "INVOICED" | "CLOSED";
                xeroStatus?: "DRAFT" | "SENT" | "ACCEPTED" | "DECLINED" | "DELETED" | "INVOICED";
                ownerId?: string;
                followUpDueBy?: string;
                late?: boolean;
                search?: string;
                sort?: "stageEnteredAt" | "quoteNumber" | "customer" | "followUpDate" | "total";
                direction?: "ASC" | "DESC";
                page?: number;
                pageSize?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WorkItemPage"];
                };
            };
        };
    };
    getMyWork: {
        parameters: {
            query?: {
                entityId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MyWork"];
                };
            };
        };
    };
    listAssignableStaff: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StaffOption"][];
                };
            };
        };
    };
    getCurrentUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["MeResponse"];
                };
            };
        };
    };
    getCombinedForecast: {
        parameters: {
            query: {
                entityIds: string[];
                financialYearStart?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CombinedView"];
                };
            };
        };
    };
    getXeroConnection: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["XeroConnection"];
                };
            };
        };
    };
    getCreateJobDraft: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateJobDraft"];
                };
            };
        };
    };
    reviewInvoiceDraft: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                itemId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["InvoiceDraftReview"];
                };
            };
        };
    };
    listInvoices: {
        parameters: {
            query?: {
                type?: "ACCREC" | "ACCPAY";
                search?: string;
                status?: "DRAFT" | "SUBMITTED" | "AUTHORISED" | "PAID" | "VOIDED" | "DELETED";
                overdue?: boolean;
                dateField?: "INVOICE_DATE" | "DUE_DATE";
                from?: string;
                to?: string;
                sort?: "DATE" | "DUE_DATE" | "AMOUNT" | "STATUS" | "CONTACT";
                direction?: "ASC" | "DESC";
                page?: number;
                pageSize?: number;
            };
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["InvoicePage"];
                };
            };
        };
    };
    getInvoice: {
        parameters: {
            query?: {
                type?: "ACCREC" | "ACCPAY";
            };
            header?: never;
            path: {
                entityId: string;
                invoiceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["InvoiceDetail"];
                };
            };
        };
    };
    listInvoiceAttachments: {
        parameters: {
            query?: {
                type?: "ACCREC" | "ACCPAY";
            };
            header?: never;
            path: {
                entityId: string;
                invoiceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["InvoiceAttachment"][];
                };
            };
        };
    };
    downloadInvoiceAttachment: {
        parameters: {
            query?: {
                type?: "ACCREC" | "ACCPAY";
                preview?: boolean;
            };
            header?: never;
            path: {
                entityId: string;
                invoiceId: string;
                attachmentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/octet-stream": string;
                };
            };
        };
    };
    getForecast: {
        parameters: {
            query?: {
                financialYearStart?: string;
            };
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ForecastView"];
                };
            };
        };
    };
    getForecastMonthItems: {
        parameters: {
            query?: {
                direction?: string;
            };
            header?: never;
            path: {
                entityId: string;
                month: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["MonthItems"];
                };
            };
        };
    };
    listForecastDocuments: {
        parameters: {
            query?: {
                type?: string;
                includeSettled?: boolean;
                page?: number;
                pageSize?: number;
            };
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DocumentPage"];
                };
            };
        };
    };
    getForecastDocument: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                entityId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StoredDocument"];
                };
            };
        };
    };
    getForecastAudit: {
        parameters: {
            query?: {
                page?: number;
                pageSize?: number;
            };
            header?: never;
            path: {
                entityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AuditPage"];
                };
            };
        };
    };
    hello: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HelloResponse"];
                };
            };
        };
    };
}
