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
            /** Format: date-time */
            lastConfirmedAt?: string;
            errorCode?: string;
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
        UpdateStaff: {
            displayName: string;
            /** @enum {string} */
            role: "DIRECTOR" | "FINANCIAL_ADMIN" | "STANDARD_USER";
            active: boolean;
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
