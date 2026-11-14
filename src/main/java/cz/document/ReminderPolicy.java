package cz.document;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class ReminderPolicy {

    public enum Action {
        NONE, GENTLE, FORMAL
    }

    private ReminderPolicy() {
    }

    public static Action decide(LocalDate dueDate,
                                LocalDate today,
                                int gentleDays,
                                int formalDays,
                                boolean gentleSent,
                                boolean formalSent,
                                boolean enabled) {
        if (!enabled || dueDate == null || today == null || formalSent) {
            return Action.NONE;
        }
        long overdue = ChronoUnit.DAYS.between(dueDate, today);
        if (overdue >= formalDays) {
            return Action.FORMAL;
        }
        if (overdue >= gentleDays && !gentleSent) {
            return Action.GENTLE;
        }
        return Action.NONE;
    }
}
