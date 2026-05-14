package com.patchagent.service;

import com.patchagent.config.PatchingProperties;
import com.patchagent.model.ServerRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Loads the server inventory from the Excel file.
 * Mirrors Python's load_servers() + COL_MAP logic exactly.
 */
@Service
public class ServerInventoryService {

    // ---------------------------------------------------------------------------
    //  Column name aliases → canonical field names  (mirrors Python COL_MAP)
    // ---------------------------------------------------------------------------
    private static final Map<String, String> COL_MAP = new HashMap<>();
    static {
        COL_MAP.put("id",                          "id");
        COL_MAP.put("cluster",                     "cluster");
        COL_MAP.put("server name",                 "serverName");
        COL_MAP.put("server",                      "serverName");
        COL_MAP.put("hostname / ip",               "host");
        COL_MAP.put("hostname/ip",                 "host");
        COL_MAP.put("hostname",                    "host");
        COL_MAP.put("ip",                          "host");
        COL_MAP.put("host",                        "host");
        COL_MAP.put("service name",                "service");
        COL_MAP.put("service",                     "service");
        COL_MAP.put("stop command",                "stopCmd");
        COL_MAP.put("stop",                        "stopCmd");
        COL_MAP.put("start command",               "startCmd");
        COL_MAP.put("start",                       "startCmd");
        COL_MAP.put("mode",                        "mode");
        COL_MAP.put("mode\n(round_robin / batch)", "mode");
        // optional: per-server RR delay
        COL_MAP.put("delay",                       "rrDelay");
        COL_MAP.put("delay (s)",                   "rrDelay");
        COL_MAP.put("delay(s)",                    "rrDelay");
        COL_MAP.put("rr delay",                    "rrDelay");
        COL_MAP.put("round robin delay",           "rrDelay");
        // optional: live status check
        COL_MAP.put("status check command",        "statusCmd");
        COL_MAP.put("status check",                "statusCmd");
        COL_MAP.put("status cmd",                  "statusCmd");
        COL_MAP.put("check command",               "statusCmd");
        // optional: sub-group for dependency scoping
        COL_MAP.put("group",                       "group");
        COL_MAP.put("server group",                "group");
        COL_MAP.put("tier",                        "group");
        COL_MAP.put("service group",               "group");
        COL_MAP.put("notes",                       "notes");
        // optional: environment tag (nonprod / prod)
        COL_MAP.put("environment",                 "environment");
        COL_MAP.put("env",                         "environment");
    }

    private static final Set<String> REQUIRED =
        Set.of("id", "cluster", "host", "service", "stopCmd", "startCmd", "mode");

    private final PatchingProperties props;

    public ServerInventoryService(PatchingProperties props) {
        this.props = props;
    }

    // ---------------------------------------------------------------------------
    //  Public API
    // ---------------------------------------------------------------------------

    /** Load all rows regardless of environment. */
    public List<ServerRow> loadServers() throws IOException {
        Path path = resolvePath(props.getExcelPath());
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Excel not found: " + path.toAbsolutePath());
        }
        try (InputStream is = Files.newInputStream(path);
             Workbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = findDataSheet(wb);
            return parseSheet(sheet);
        }
    }

    /**
     * Load rows filtered to the given environment ("nonprod" or "prod").
     * Rows whose environment column is blank are included in every environment
     * for backwards-compatibility with Excel files that predate this column.
     */
    public List<ServerRow> loadServers(String environment) throws IOException {
        List<ServerRow> all = loadServers();
        if (environment == null || environment.isBlank()) return all;
        return all.stream()
                  .filter(r -> r.getEnvironment() == null
                            || r.getEnvironment().isBlank()
                            || r.getEnvironment().equalsIgnoreCase(environment))
                  .toList();
    }

    public List<String> loadClusters() throws IOException {
        List<ServerRow> rows = loadServers();
        return rows.stream()
                   .map(ServerRow::getCluster)
                   .distinct()
                   .sorted()
                   .toList();
    }

    // ---------------------------------------------------------------------------
    //  Private helpers
    // ---------------------------------------------------------------------------

    private Sheet findDataSheet(Workbook wb) {
        // Use first sheet that isn't named "legend" (case-insensitive)
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String name = wb.getSheetName(i).toLowerCase().trim();
            if (!name.equals("legend")) return wb.getSheetAt(i);
        }
        return wb.getSheetAt(0);
    }

    private List<ServerRow> parseSheet(Sheet sheet) {
        Iterator<Row> rowIter = sheet.iterator();
        if (!rowIter.hasNext()) throw new IllegalStateException("Sheet is empty");

        // Parse header row
        Row headerRow = rowIter.next();
        List<String> headers = new ArrayList<>();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();

        for (Cell cell : headerRow) {
            String raw = cellToString(cell, evaluator).strip().toLowerCase();
            String canonical = COL_MAP.getOrDefault(raw, raw.replace(" ", ""));
            headers.add(canonical);
        }

        // Validate required columns
        Set<String> presentCols = new HashSet<>(headers);
        Set<String> missing = new HashSet<>(REQUIRED);
        missing.removeAll(presentCols);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Excel missing required columns: " + missing);
        }

        // Parse data rows
        List<ServerRow> servers = new ArrayList<>();
        while (rowIter.hasNext()) {
            Row row = rowIter.next();
            Map<String, String> rec = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                rec.put(headers.get(i), cell == null ? "" : cellToString(cell, evaluator).strip());
            }

            String host = rec.getOrDefault("host", "").strip();
            if (host.isEmpty()) continue;  // skip blank rows

            servers.add(buildRow(rec));
        }
        return servers;
    }

    private ServerRow buildRow(Map<String, String> rec) {
        ServerRow row = new ServerRow();

        // ID
        try { row.setId(Integer.parseInt(rec.getOrDefault("id", "0"))); }
        catch (NumberFormatException e) { row.setId(0); }

        // Mode
        String mode = rec.getOrDefault("mode", "batch").strip().toLowerCase();
        row.setMode(mode.equals("round_robin") ? "round_robin" : "batch");

        row.setCluster(orDefault(rec.get("cluster"), "Default"));
        row.setServerName(orDefault(rec.get("serverName"), rec.getOrDefault("host", "")));
        row.setHost(rec.getOrDefault("host", ""));
        row.setService(orDefault(rec.get("service"), "service"));
        row.setStopCmd(orDefault(rec.get("stopCmd"), ""));
        row.setStartCmd(orDefault(rec.get("startCmd"), ""));
        row.setStatusCmd(orDefault(rec.get("statusCmd"), ""));
        row.setGroup(orDefault(rec.get("group"), ""));
        row.setNotes(orDefault(rec.get("notes"), ""));
        row.setEnvironment(orDefault(rec.get("environment"), ""));

        // Per-server RR delay
        String rawDelay = rec.get("rrDelay");
        if (rawDelay != null && !rawDelay.isEmpty()) {
            try { row.setRrDelay(Double.parseDouble(rawDelay)); }
            catch (NumberFormatException e) { row.setRrDelay(null); }
        }

        return row;
    }

    // ---------------------------------------------------------------------------
    //  Cell utilities
    // ---------------------------------------------------------------------------

    private String cellToString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            CellValue cv = evaluator.evaluate(cell);
            type = cv.getCellType();
            return switch (type) {
                case STRING  -> cv.getStringValue();
                case NUMERIC -> formatNumeric(cv.getNumberValue());
                case BOOLEAN -> String.valueOf(cv.getBooleanValue());
                default      -> "";
            };
        }
        return switch (type) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> formatNumeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    private String formatNumeric(double d) {
        // Return integer string if it has no fractional part
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private String orDefault(String value, String def) {
        return (value == null || value.isBlank()) ? def : value.strip();
    }

    private Path resolvePath(String pathStr) {
        Path p = Path.of(pathStr);
        return p.isAbsolute() ? p : Path.of("").toAbsolutePath().resolve(p);
    }
}
