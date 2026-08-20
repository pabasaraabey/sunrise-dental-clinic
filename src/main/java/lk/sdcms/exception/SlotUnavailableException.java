package lk.sdcms.exception;

import java.time.LocalDate;
import java.time.LocalTime;

/** Raised when a dentist already has an appointment in the requested slot. */
public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException(Long dentistId, LocalDate date, LocalTime time) {
        super("Dentist " + dentistId + " is already booked on "
              + date + " at " + time);
    }
}
