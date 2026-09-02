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
                { method: 'POST' }),

        /* -----------------------------------------------------------
           Dentists, treatments, patients, staff
           ----------------------------------------------------------- */

        dentists: (activeOnly) =>
            request('api/dentists' + (activeOnly ? '?activeOnly=true' : '')),

        saveDentist: (dentist) =>
            request('api/dentists', {
                method: 'POST', body: JSON.stringify(dentist)
            }),

        setDentistActive: (id, active) =>
            request('api/dentists?action=' + (active ? 'reinstate' : 'retire')
                + '&id=' + encodeURIComponent(id), { method: 'POST' }),

        treatments: (activeOnly) =>
            request('api/treatments' + (activeOnly ? '?activeOnly=true' : '')),

        saveTreatment: (treatment) =>
            request('api/treatments', {
                method: 'POST', body: JSON.stringify(treatment)
            }),

        setTreatmentActive: (id, active) =>
            request('api/treatments?action=' + (active ? 'reinstate' : 'retire')
                + '&id=' + encodeURIComponent(id), { method: 'POST' }),

        patients: () => request('api/patients'),

        patient: (id) => request('api/patients?id=' + encodeURIComponent(id)),

        staff: () => request('api/users'),

        createStaff: (account) =>
            request('api/users', { method: 'POST', body: JSON.stringify(account) }),

        setStaffActive: (id, active) =>
            request('api/users?action=' + (active ? 'enable' : 'disable')
                + '&id=' + encodeURIComponent(id), { method: 'POST' }),

        resetStaffPassword: (id, password) =>
            request('api/users?action=resetPassword&id=' + encodeURIComponent(id)
                + '&password=' + encodeURIComponent(password), { method: 'POST' }),

        /* -----------------------------------------------------------
           Reports
           ----------------------------------------------------------- */

        report: (type, params = {}) => {
            const query = new URLSearchParams({ type, ...params }).toString();
            return request('api/reports?' + query);
        },

        /* Closing out a visit. Cancellation has its own endpoint because it
           carries the two-hour cutoff rule. */
        setAppointmentStatus: (appointmentNo, status) =>
            request('api/appointments/status?no=' + encodeURIComponent(appointmentNo)
                + '&status=' + encodeURIComponent(status), { method: 'POST' })
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
        const both = ['ADMINISTRATOR', 'RECEPTIONIST'];

        const links = [
            { href: 'index.html',      label: 'Dashboard',       roles: both },
            { href: 'booking.html',    label: 'New appointment', roles: both },
            { href: 'search.html',     label: 'Find appointment', roles: both },
            { href: 'billing.html',    label: 'Billing',         roles: both },
            { href: 'patients.html',   label: 'Patients',        roles: both },
            { href: 'dentists.html',   label: 'Dentists',        roles: both },
            { href: 'treatments.html', label: 'Treatments',      roles: both },
            { href: 'reports.html',    label: 'Reports',         roles: both },
            // Staff accounts are the one thing reserved for the administrator.
            { href: 'staff.html',      label: 'Staff accounts',  roles: ['ADMINISTRATOR'] },
            { href: 'help.html',       label: 'Help',            roles: both }
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
