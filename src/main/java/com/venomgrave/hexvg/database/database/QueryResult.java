package com.venomgrave.hexvg.database.database;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryResult {

    private final List<Map<String, Object>> rows;
    private final int affectedRows;

    private QueryResult(List<Map<String, Object>> rows, int affectedRows) {
        this.rows = Collections.unmodifiableList(rows);
        this.affectedRows = affectedRows;
    }

    public static QueryResult fromResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String col = meta.getColumnLabel(i).toLowerCase();
                row.put(col, rs.getObject(i));
            }
            rows.add(row);
        }
        return new QueryResult(rows, -1);
    }

    public static QueryResult fromUpdate(int affectedRows) {
        return new QueryResult(new ArrayList<>(), affectedRows);
    }

    public static QueryResult empty() {
        return new QueryResult(new ArrayList<>(), 0);
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public boolean hasRows() {
        return !rows.isEmpty();
    }

    public Object getValue(int rowIndex, String column) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return null;
        return rows.get(rowIndex).get(column.toLowerCase());
    }

    public Map<String, Object> getRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return null;
        return rows.get(rowIndex);
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }
}
