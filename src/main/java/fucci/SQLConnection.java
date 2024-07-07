package fucci;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLConnection {
    private final Connection connection;

    public SQLConnection(Connection connection) {
        this.connection = connection;
    }

    public String getDatabaseVersion() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        return meta.getDatabaseProductVersion();
    }

    public String getConnectionURL() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        return meta.getURL();
    }

    public String getDatabaseName() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        return meta.getDatabaseProductName().toLowerCase();
    }

    public void close() throws SQLException {
        connection.close();
    }

    public Statement prepareStatement(String arg) throws SQLException {
        return connection.prepareStatement(arg);
    }

    public Statement createStatement() throws SQLException {
        return connection.createStatement();
    }

    public DatabaseMetaData getMetaData() {
        try {
            return connection.getMetaData();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
