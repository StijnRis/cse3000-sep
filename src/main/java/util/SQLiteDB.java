package util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class SQLiteDB {

    public Connection connection = null;

    public SQLiteDB(String sqliteLocation) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteLocation);
        } catch (SQLException e) {
            Log.error("Failed setting up SQLite db." + e);
            System.exit(-1);
        }
    }

    public void setupSchema() throws IOException, URISyntaxException, SQLException {
        if(connection == null) {
            Log.error("No DB Connection");
            return;
        }

        URL schemaURL = Thread.currentThread().getContextClassLoader().getResource("schema.sql");
        List<String> schema = Files.readAllLines(Paths.get(schemaURL.toURI()));

        Statement statement = connection.createStatement();
        statement.executeUpdate(String.join("\n", schema));
        statement.close();
    }

    public void close() throws SQLException {
        connection.close();
    }
}
