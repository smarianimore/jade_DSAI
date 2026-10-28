import java.util.Locale;

/** Shared payoff model for the battle-of-the-sexes example. */
final class BattleOfSexes {
    static final String MOVIE = "MOVIE";
    static final String FRIENDS = "FRIENDS";

    private BattleOfSexes() {
    }

    static boolean isValidSchedule(String schedule) {
        if (schedule == null || schedule.trim().isEmpty()) {
            return false;
        }
        for (String event : schedule.split(",")) {
            String choice = event.trim().toUpperCase(Locale.ROOT);
            if (!MOVIE.equals(choice) && !FRIENDS.equals(choice)) {
                return false;
            }
        }
        return true;
    }

    static int husbandUtility(String schedule) {
        return count(schedule, MOVIE) * 2 + count(schedule, FRIENDS);
    }

    static int wifeUtility(String schedule) {
        return count(schedule, FRIENDS) * 2 + count(schedule, MOVIE);
    }

    private static int count(String schedule, String choice) {
        int count = 0;
        for (String event : schedule.split(",")) {
            if (choice.equals(event.trim().toUpperCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }
}
