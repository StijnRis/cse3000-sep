package util;

import lombok.Getter;

@Getter
public class SQLiteDB {

//    private ConnectionSource connection = null;
//
//    //DAOs
//    private Dao<Project, Integer> projectDao = null;
//    private Dao<Person, Integer> personDao = null;
//    private Dao<Organisation, Integer> organisationDao = null;
//    private Dao<PersonUsername, Integer> personUsernameDao = null;
//
//
//    public SQLiteDB(String sqliteLocation) {
//        try {
//            connection = new JdbcConnectionSource("jdbc:sqlite:" + sqliteLocation);
//        } catch (SQLException e) {
//            Log.error("Failed setting up SQLite db." + e);
//            System.exit(-1);
//        }
//    }
//
//    public ConnectionSource getConnection() {
//        return connection;
//    }
//
//    public void setupSchema() throws SQLException {
//        projectDao = clearAndCreate(Project.class);
//        personDao = clearAndCreate(Person.class);
//        organisationDao = clearAndCreate(Organisation.class);
//        personUsernameDao = clearAndCreate(PersonUsername.class);
//    }
//
//    private <D extends Dao<T, ?>, T> D clearAndCreate(Class<?> entity) throws SQLException {
//        Dao<T, ?> dao = (Dao<T, ?>) DaoManager.createDao(connection, entity);
//        TableUtils.createTableIfNotExists(connection, entity);
//
//        return (D) dao;
//    }
//
//    //    public void setupSchema() throws IOException, URISyntaxException, SQLException {
////        if(connection == null) {
////            Log.error("No DB Connection");
////            return;
////        }
////
////        URL schemaURL = Thread.currentThread().getContextClassLoader().getResource("schema.sql");
////        List<String> schema = Files.readAllLines(Paths.get(schemaURL.toURI()));
////
////        Statement statement = connection.createStatement();
////        statement.executeUpdate(String.join("\n", schema));
////        statement.close();
////    }
//
//    public void close() throws Exception {
//        connection.close();
//    }
}
