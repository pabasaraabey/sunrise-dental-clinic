/* =====================================================================
   Client-side validation.

   These checks exist to give the receptionist immediate feedback while a
   patient is standing at the desk — not to protect the system. Anyone can
   open developer tools, edit this file's behaviour, or call the endpoints
   directly with Postman, so every rule here is enforced again in the Java
   service layer. Treat this as a convenience, never as a control.
   ===================================================================== */

const Validate = (() => {

    /* Sri Lankan mobile and landline numbers: ten digits beginning with 0. */
    const CONTACT_PATTERN = /^0\d{9}$/;

    const CLINIC_OPENS  = '08:00';
    const CLINIC_CLOSES = '20:00';
    const MAX_ADVANCE_DAYS = 90;

    function markInvalid(fieldId, message) {
        const field = document.getElementById(fieldId).closest('.field');
        field.classList.add('invalid');
        const error = field.querySelector('.field-error');
        if (error) error.textContent = message;
        return false;
    }

    function markValid(fieldId) {
        document.getElementById(fieldId).closest('.field').classList.remove('invalid');
        return true;
    }

    function clearAll(formId) {
        document.querySelectorAll('#' + formId + ' .field.invalid')
            .forEach(f => f.classList.remove('invalid'));
    }

    return {
        markInvalid,
        markValid,
        clearAll,

        required(fieldId, label) {
            const value = document.getElementById(fieldId).value.trim();
            return value === ''
                ? markInvalid(fieldId, label + ' is required')
                : markValid(fieldId);
        },

        contactNo(fieldId) {
            const value = document.getElementById(fieldId).value.trim();
            if (value === '') {
                return markInvalid(fieldId, 'Contact number is required');
            }
            if (!CONTACT_PATTERN.test(value)) {
                return markInvalid(fieldId,
                    'Enter 10 digits beginning with 0, for example 0771234567');
            }
            return markValid(fieldId);
        },

        /** Date of birth must exist and cannot be in the future. */
        dateOfBirth(fieldId) {
            const value = document.getElementById(fieldId).value;
            if (!value) {
                return markInvalid(fieldId, 'Date of birth is required');
            }
            if (value > new Date().toISOString().slice(0, 10)) {
                return markInvalid(fieldId, 'Date of birth cannot be in the future');
            }
            return markValid(fieldId);
        },

        /** Appointment date: today or later, within the advance booking limit. */
        appointmentDate(fieldId) {
            const value = document.getElementById(fieldId).value;
            if (!value) {
                return markInvalid(fieldId, 'Appointment date is required');
            }

            const today = new Date().toISOString().slice(0, 10);
            if (value < today) {
                return markInvalid(fieldId, 'Appointments cannot be booked in the past');
            }

            const limit = new Date();
            limit.setDate(limit.getDate() + MAX_ADVANCE_DAYS);
            if (value > limit.toISOString().slice(0, 10)) {
                return markInvalid(fieldId,
                    'Appointments cannot be booked more than '
                    + MAX_ADVANCE_DAYS + ' days ahead');
            }
            return markValid(fieldId);
        },

        selected(fieldId, label) {
            const value = document.getElementById(fieldId).value;
            return (!value || value === '')
                ? markInvalid(fieldId, 'Please choose a ' + label)
                : markValid(fieldId);
        },

        /** Optional field: valid when empty, checked only when filled in. */
        emailIfPresent(fieldId) {
            const value = document.getElementById(fieldId).value.trim();
            if (value === '') return markValid(fieldId);
            return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
                ? markValid(fieldId)
                : markInvalid(fieldId, 'Enter a valid email address');
        },

        withinClinicHours(time) {
            return time >= CLINIC_OPENS && time < CLINIC_CLOSES;
        }
    };
})();
