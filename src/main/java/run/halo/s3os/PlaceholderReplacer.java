package run.halo.s3os;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class PlaceholderReplacer {
    record PlaceholderFunctionInput(String[] placeholderParams,
                                    Map<String, String> reusableParams) {
    }

    private static final Map<String, Function<PlaceholderFunctionInput, String>>
        placeholderFunctions = new HashMap<>();

    static {
        initializePlaceholderFunctions();
    }

    private static void initializePlaceholderFunctions() {
        placeholderFunctions.put("origin-filename", input -> input.reusableParams.get("filename"));
        placeholderFunctions.put("uuid-with-dash", input -> generateUUIDWithDash());
        placeholderFunctions.put("uuid-no-dash", input -> generateUUIDWithoutDash());
        placeholderFunctions.put("timestamp-sec",
            input -> currentSecondsTimestamp(input.reusableParams));
        placeholderFunctions.put("timestamp-ms",
            input -> currentMillisecondsTimestamp(input.reusableParams));
        placeholderFunctions.put("year", input -> currentYear(input.reusableParams));
        placeholderFunctions.put("month", input -> currentMonth(input.reusableParams));
        placeholderFunctions.put("day", input -> currentDay(input.reusableParams));
        placeholderFunctions.put("weekday", input -> currentWeekday(input.reusableParams));
        placeholderFunctions.put("hour", input -> currentHour(input.reusableParams));
        placeholderFunctions.put("minute", input -> currentMinute(input.reusableParams));
        placeholderFunctions.put("second", input -> currentSecond(input.reusableParams));
        placeholderFunctions.put("millisecond", input -> currentMillisecond(input.reusableParams));
        placeholderFunctions.put("random-alphabetic",
            input -> generateRandomLetter(input.placeholderParams));
        placeholderFunctions.put("random-num",
            input -> generateRandomNumber(input.placeholderParams));
        placeholderFunctions.put("random-alphanumeric",
            input -> generateRandomAlphanumeric(input.placeholderParams));
    }

    private static String generateRandomAlphanumeric(String[] placeholderParams) {
        try {
            int length = Integer.parseInt(placeholderParams[0]);
            return RandomStringUtils.randomAlphanumeric(length).toLowerCase();
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return RandomStringUtils.randomAlphanumeric(8).toLowerCase();
        }
    }

    private static String generateRandomNumber(String[] placeholderParams) {
        try {
            int length = Integer.parseInt(placeholderParams[0]);
            return RandomStringUtils.randomNumeric(length);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return RandomStringUtils.randomNumeric(8);
        }
    }

    private static String currentMillisecond(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.valueOf(time.getNano() / 1000000);
    }

    private static String currentSecond(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.format("%02d", time.getSecond());
    }

    private static String currentMinute(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.format("%02d", time.getMinute());
    }

    private static String currentHour(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.format("%02d", time.getHour());
    }

    private static String currentWeekday(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.valueOf(time.getDayOfWeek().getValue());
    }

    private static String currentDay(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.format("%02d", time.getDayOfMonth());
    }

    private static String currentMonth(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.format("%02d", time.getMonthValue());
    }

    private static String currentYear(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.valueOf(time.getYear());
    }

    private static String currentMillisecondsTimestamp(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.valueOf(
            time.toInstant(ZoneOffset.systemDefault().getRules().getOffset(time)).toEpochMilli());
    }

    private static String currentSecondsTimestamp(Map<String, String> reusableParams) {
        LocalDateTime time = LocalDateTime.parse(reusableParams.get("time"));
        return String.valueOf(
            time.toInstant(ZoneOffset.systemDefault().getRules().getOffset(time)).getEpochSecond());
    }


    private static String generateRandomLetter(String[] placeholderParams) {
        try {
            int length = Integer.parseInt(placeholderParams[0]);
            return RandomStringUtils.randomAlphabetic(length).toLowerCase();
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return RandomStringUtils.randomAlphabetic(8).toLowerCase();
        }
    }

    private static String generateUUIDWithoutDash() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private static String generateUUIDWithDash() {
        return UUID.randomUUID().toString().toUpperCase();
    }

    public static String replacePlaceholders(String template, String filename) {
        if (StringUtils.isBlank(template)) {
            return filename;
        }
        Map<String, String> reusableParams = new HashMap<>();

        reusableParams.put("filename", filename);
        reusableParams.put("time", LocalDateTime.now().toString());

        StringBuilder result = new StringBuilder();
        int startIndex = 0;
        int endIndex;

        while ((endIndex = template.indexOf("${", startIndex)) != -1) {
            result.append(template, startIndex, endIndex);

            int closingBracketIndex = template.indexOf('}', endIndex);
            if (closingBracketIndex != -1) {
                String placeholderWithParam = template.substring(endIndex + 2, closingBracketIndex);
                String replacement = getPlaceholderValue(placeholderWithParam, reusableParams);
                result.append(replacement);

                startIndex = closingBracketIndex + 1;
            } else {
                // If no closing bracket is found, append the rest of the template and break
                startIndex = endIndex;
                break;
            }
        }

        // Append the remaining part of the template
        if (startIndex < template.length()) {
            result.append(template, startIndex, template.length());
        }

        return result.toString();
    }

    private static String getPlaceholderValue(String placeholderWithParam,
                                              Map<String, String> reusableParams) {
        String[] parts = placeholderWithParam.split(":");
        String placeholder = parts[0];

        String[] placeholderParams;
        if (parts.length > 1) {
            placeholderParams = new String[parts.length - 1];
            System.arraycopy(parts, 1, placeholderParams, 0, parts.length - 1);
        } else {
            placeholderParams = new String[0];
        }

        Function<PlaceholderFunctionInput, String> placeholderFunction =
            placeholderFunctions.get(placeholder);
        if (placeholderFunction != null) {
            // Call the placeholder function with the provided map
            return placeholderFunction.apply(
                new PlaceholderFunctionInput(placeholderParams, reusableParams));
        } else {
            // If placeholder not found, return the original placeholder
            return "${" + placeholderWithParam + "}";
        }
    }

}
