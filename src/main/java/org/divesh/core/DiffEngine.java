package org.divesh.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiffEngine {

    public List<DiffLine> diff(String oldContent, String newContent) {
        // split into lines
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        // build LCS table
        int[][] dp = buildLCSTable(oldLines, newLines);

        // backtrack to get diff
        return backtrack(dp, oldLines, newLines,
                oldLines.length, newLines.length);
    }

    private int[][] buildLCSTable(String[] old, String[] neu) {
        int m = old.length;
        int n = neu.length;
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (old[i-1].equals(neu[j-1])) {
                    dp[i][j] = dp[i-1][j-1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i-1][j], dp[i][j-1]);
                }
            }
        }
        return dp;
    }

    private List<DiffLine> backtrack(int[][] dp,
                                     String[] old, String[] neu, int i, int j) {

        List<DiffLine> result = new ArrayList<>();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && old[i-1].equals(neu[j-1])) {
                // lines match — unchanged
                result.add(new DiffLine(DiffLine.Type.EQUAL, old[i-1]));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j-1] >= dp[i-1][j])) {
                // line added in new
                result.add(new DiffLine(DiffLine.Type.ADD, neu[j-1]));
                j--;
            } else {
                // line removed from old
                result.add(new DiffLine(DiffLine.Type.REMOVE, old[i-1]));
                i--;
            }
        }

        // backtrack builds in reverse — flip it
        Collections.reverse(result);
        return result;
    }

    public String format(List<DiffLine> lines,
                         String oldName, String newName) {

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(oldName).append("\n");
        sb.append("+++ ").append(newName).append("\n");

        // find first change for @@ header
        int firstChange = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).type() != DiffLine.Type.EQUAL) {
                firstChange = i + 1;
                break;
            }
        }

        sb.append("@@ -").append(firstChange)
                .append(" +").append(firstChange).append(" @@\n");

        for (DiffLine line : lines) {
            switch (line.type()) {
                case ADD    -> sb.append("+").append(line.content()).append("\n");
                case REMOVE -> sb.append("-").append(line.content()).append("\n");
                case EQUAL  -> sb.append(" ").append(line.content()).append("\n");
            }
        }

        return sb.toString();
    }
}