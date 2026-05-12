package com.patchagent.util;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Parses a row selection string like "1,3,5-10,14" into a sorted list of IDs.
 * Mirrors Python's parse_row_selection() exactly.
 */
@Component
public class RowSelectionParser {

    /**
     * @param selection e.g. "1,3,5-10"  or  "*"  or  "" (empty)
     * @param allIds    all valid IDs in the inventory
     * @return sorted list of matched IDs
     */
    public List<Integer> parse(String selection, List<Integer> allIds) {
        if (selection == null) selection = "";
        selection = selection.strip();

        if (selection.isEmpty() || selection.equals("*")) {
            List<Integer> result = new ArrayList<>(allIds);
            Collections.sort(result);
            return result;
        }

        Set<Integer> valid  = new HashSet<>(allIds);
        Set<Integer> result = new TreeSet<>();

        for (String part : selection.split(",")) {
            part = part.strip();
            if (part.contains("-")) {
                String[] bounds = part.split("-", 2);
                try {
                    int lo = Integer.parseInt(bounds[0].strip());
                    int hi = Integer.parseInt(bounds[1].strip());
                    for (int i = lo; i <= hi; i++) {
                        if (valid.contains(i)) result.add(i);
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                try {
                    int n = Integer.parseInt(part);
                    if (valid.contains(n)) result.add(n);
                } catch (NumberFormatException ignored) {}
            }
        }

        return new ArrayList<>(result);
    }
}
