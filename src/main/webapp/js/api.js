/* =====================================================================
   Thin wrapper over fetch.

   Carries the JSESSIONID cookie, parses JSON, and throws an error that
   preserves the HTTP status. Pages need that status to tell 409 (slot
   taken) from 422 (too late to cancel) from 401 (session expired) —
   collapsing them into one generic failure would make it impossible to
   show the right message.
   ===================================================================== */

const Api = (() => {

    class ApiError extends Error {
        constructor(status, message, body) {
            super(message);
            this.status = status;
            this.body = body;
        }
    }

    async function request(path, options = {}) {
        const response = await fetch(path, {
            // Same-origin: the frontend is served by the same Tomcat instance
            // as the API, so the session cookie travels without CORS.
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            ...options
        });

        // 204 has no body to parse.
        if (response.status === 204) return null;

        const text = await response.text();
        let body = null;
        if (text) {
            try { body = JSON.parse(text); } catch { body = text; }
        }

        if (!response.ok) {
            const message = (body && body.message)
                ? body.message
                : 'Request failed (' + response.status + ')';
            throw new ApiError(response.status, message, body);
        }
        return body;
    }

    return {
        ApiError,

        get:  (path)       => request(path),
        post: (path, data) => request(path, {
            method: 'POST',
            body: data === undefined ? undefined : JSON.stringify(data)
        }),

        /* -----------------------------------------------------------
           Session
           ----------------------------------------------------------- */

        login: (username, password) =>
            request('api/auth/login', {
                method: 'POST',
                body: JSON.stringify({ username, password })
            }),

        logout: () => request('api/auth/logout', { method: 'POST' }),

        session: () => request('api/auth/session'),

        /* -----------------------------------------------------------
           Reference data, appointments, bills
           ----------------------------------------------------------- */

        reference: () => request('api/reference'),

        availability: (dentistId, date) =>
            request('api/appointments/availability?dentistId='
                + encodeURIComponent(dentistId) + '&date=' + encodeURIComponent(date)),

        bookAppointment: (payload) =>
            request('api/appointments', {
                method: 'POST',
                body: JSON.stringify(payload)
            }),

        findAppointment: (no) =>
            request('api/appointments?no=' + encodeURIComponent(no)),

        appointmentsOn: (date) =>
            request('api/appointments?date=' + encodeURIComponent(date)),

        cancelAppointment: (no) =>
            request('api/appointments/cancel?no=' + encodeURIComponent(no),
                { method: 'POST' }),

        findBill: (appointmentNo) =>
            request('api/bills?appointmentNo=' + encodeURIComponent(appointmentNo)),

        generateBill: (appointmentNo) =>
            request('api/bills?appointmentNo=' + encodeURIComponent(appointmentNo),
                { method: 'POST' })
    };
})();


/* =====================================================================
   Shared page helpers
   ===================================================================== */

const Ui = {

    /** Guards a page. Returns the signed-in user, or redirects to login. */
    async requireSession() {
        try {
            return await Api.session();
        } catch (e) {
            window.location.href = 'login.html';
            throw e;
        }
    },

    /** Renders the sidebar, showing only what this role may use. */
    renderShell(user, activePage) {
        const links = [
            { href: 'index.html',   label: 'Dashboard',      roles: ['ADMINISTRATOR', 'RECEPTIONIST', 'DENTIST'] },
            { href: 'booking.html', label: 'New appointment', roles: ['ADMINISTRATOR', 'RECEPTIONIST'] },
            { href: 'search.html',  label: 'Find appointment', roles: ['ADMINISTRATOR', 'RECEPTIONIST', 'DENTIST'] },
            { href: 'billing.html', label: 'Billing',        roles: ['ADMINISTRATOR', 'RECEPTIONIST'] },
            { href: 'reports.html', label: 'Reports',        roles: ['ADMINISTRATOR', 'RECEPTIONIST', 'DENTIST'] },
            { href: 'help.html',    label: 'Help',           roles: ['ADMINISTRATOR', 'RECEPTIONIST', 'DENTIST'] }
        ];

        const nav = links
            .filter(l => l.roles.includes(user.role))
            .map(l => '<a href="' + l.href + '"'
                + (l.href === activePage ? ' class="active"' : '')
                + '>' + l.label + '</a>')
            .join('');

        // Hiding a link is a convenience, not a control. The server re-checks
        // the caller's role on every request regardless of what was rendered.
        document.getElementById('sidebar').innerHTML =
            '<div class="brand"><strong>Sunrise Dental</strong>'
            + '<span>Clinic management</span></div>'
            + '<nav>' + nav + '</nav>'
            + '<div class="who"><strong>' + Ui.escape(user.fullName) + '</strong>'
            + '<span class="role">' + user.role.toLowerCase() + '</span>'
            + '<button class="btn-secondary" style="margin-top:10px;width:100%"'
            + ' onclick="Ui.signOut()">Sign out</button></div>';
    },

    async signOut() {
        try { await Api.logout(); } catch { /* leaving anyway */ }
        window.location.href = 'login.html';
    },

    notice(id, type, message) {
        const el = document.getElementById(id);
        el.className = 'notice show ' + type;
        el.textContent = message;
    },

    clearNotice(id) {
        document.getElementById(id).className = 'notice';
    },

    escape(text) {
        if (text === null || text === undefined) return '';
        return String(text)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    },

    money(value) {
        if (value === null || value === undefined) return '—';
        return Number(value).toLocaleString('en-LK', {
            minimumFractionDigits: 2, maximumFractionDigits: 2
        });
    },

    today() {
        return new Date().toISOString().slice(0, 10);
    }
};
