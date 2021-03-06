/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ulss.se2db.metastore;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author alexmu
 */
public class TableSe2DBRule {

    String ruleName;
    String mqName;
    String dbType;
    String connStr;
    String userName;
    String password;
    String tableName;
    List<Column> columnSet = new ArrayList<Column>();

    public TableSe2DBRule(String pRuleName, String pMQName, String pDBType, String pJDBCURL, String pUserName, String pPassword, String pTableName) {
        ruleName = pRuleName;
        mqName = pMQName;
        dbType = pDBType;
        connStr = pJDBCURL;
        userName = pUserName;
        password = pPassword;
        tableName = pTableName;
    }

    public void parseColumSet(String pColumnSetStr) {
        String[] columns = pColumnSetStr.split(":");
        for (String column : columns) {
            String[] columnItems = column.split(",");
            columnSet.add(new Column(columnItems[0], Integer.parseInt(columnItems[1])));
        }
    }

    public String getMqName() {
        return mqName;
    }

    public String getTableName() {
        return tableName;
    }

    public String getDbType() {
        return dbType;
    }

    public List<Column> getColumnSet() {
        return columnSet;
    }

    public String getConnStr() {
        return connStr;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getRuleName() {
        return ruleName;
    }

    public class Column {

        String columnName;
        int columnIdx;

        public Column(String pColumnName, int pColumnIdx) {
            columnName = pColumnName;
            columnIdx = pColumnIdx;
        }
        

        public String getColumnName() {
            return columnName;
        }

        public int getColumnIdx() {
            return columnIdx;
        }
    }
}
