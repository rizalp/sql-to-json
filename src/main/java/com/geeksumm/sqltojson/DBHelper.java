package com.geeksumm.sqltojson;

import java.sql.*;
import java.util.*;

public class DBHelper {
	private final String JDBC_DRIVER;
	private final String HOST;
	private final String DATABASE;
	private final String USER;
	private final String PASSWORD;
	private final String QUERY;
	private Connection c;
	private String url;
	private static String tableName;
	private ResultSetMetaData rsmd;
	private ResultSet rs;
	private Statement stmt;
	private DatabaseMetaData meta;
	private int columnCount;

	private Map<Integer, String> tableNames = new HashMap<>();
	private Map<String, Integer> tableStartPos = new HashMap<>();
	private Map<String, Integer> tableEndPos = new HashMap<>();

	// To hold one column related Primary Keys Column Position
	// Map<column, result set keys>
	private Map<Integer, List<Integer>> pkPerColumn;
	public Map<Integer, List<Integer>> getPkPerColumn() {
		return this.pkPerColumn;
	}

	// get the foreign key of each table that is the same as DocRoot
	private Set<Integer> foreignKeyColumn;
	public Set<Integer> getForeignKeyColumn() {
		return this.foreignKeyColumn;
	}

	public ResultSetMetaData getRsmd() {
		return this.rsmd;
	}

	// used only when writeNormalized
	public static String getTableName() {
		return tableName;
	}

	public DBHelper(Config config) throws SQLException {
		switch (config.getJdbcDriver().toString()) {
			case "mysql":
			case "mariadb":
				JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
				url = "jdbc:mysql://";
				break;
			case "postgresql":
				System.out.println("Not implemented yet");
				JDBC_DRIVER = "";
				System.exit(1);
				break;
			case "sqlserver":
				System.out.println("Not implemented yet");
				JDBC_DRIVER = "";
				System.exit(1);
				break;
			case "oracle":
				System.out.println("Not implemented yet");
				JDBC_DRIVER = "";
				System.exit(1);
				break;
			default:
				System.out.println("Please specify the JDBC Driver to be used");
				JDBC_DRIVER = "";
				System.exit(1);
				break;
		}

		HOST = config.getHost();
		DATABASE = config.getDatabase();
		USER = config.getUser();
		PASSWORD = config.getPassword();
		QUERY = config.getQuery();

		try {
			Class.forName(JDBC_DRIVER);
			url = url + HOST + "/" + DATABASE;
			c = DriverManager.getConnection(url, USER, PASSWORD);
		} catch (ClassNotFoundException c) {
			System.out.println(c.toString() + " Check your JDBC Driver");
			c.printStackTrace();
		} catch (SQLException s) {
			System.out.println(s.toString()
					+ " There's a problem in your SQL Statement or Database");
			s.printStackTrace();
		}

		calculateMetaData();
	}

	// calculate database metadata that's required for our OEM Tree operation
	private void calculateMetaData() throws SQLException {
		this.meta = this.c.getMetaData();
		stmt = c.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY,
				java.sql.ResultSet.CONCUR_READ_ONLY);
		// fetch one row is enough
		stmt.setFetchSize(Integer.MIN_VALUE);
		rs = stmt.executeQuery(QUERY);
		rsmd = rs.getMetaData();
		columnCount = rsmd.getColumnCount();

		// close the connections, so we can query another resource
		stmt.close();
		rs.close();

		resultSetTablePos();
		resultSetPKPos();
		resultSetFKPos();
	}

	// Get The Table Name and It's position in the ResultSet
	private void resultSetTablePos() throws SQLException {
		String currentTableName;
		tableNames = new HashMap<>();
		tableStartPos = new HashMap<>();
		tableEndPos = new HashMap<>();
		for (int i = 1; i <= columnCount; i++) {
			currentTableName = rsmd.getTableName(i);
			if (!tableStartPos.containsKey(currentTableName)) {
				tableStartPos.put(currentTableName, i);
			} else {
				tableEndPos.put(currentTableName, i);
			}
			tableNames.put(i, currentTableName);
		}
	}

	/*
	 * Since result set are probably the result of many tables joined together,
	 * we need to take that into account by mapping each column into it's
	 * subsequent PK. The mapping will be useful later when processing OEM Tree
	 * into JSON Document.
	 * 
	 * This method do: find out the table name from where those columns
	 * originated from, get the primary keys in that table, create index
	 * (pkPerColumn) which mapping each column with its subsequent primary key
	 * in the result set.
	 */
	private void resultSetPKPos() throws SQLException {
		ResultSet tablePrimaryKeys = null;
		String currentTableName, key;
		this.pkPerColumn = new HashMap<>();
		List<Integer> keys;

		// get the Primary Key Sequence from ALL of the Table
		// this is ugly. rewrite it if possible
		for (int i = 1; i <= columnCount; i++) {
			tableName = tableNames.get(i);

			// Retrieves a description of the given table's primary key columns.
			tablePrimaryKeys = meta.getPrimaryKeys(null, null, tableName);
			while (tablePrimaryKeys.next()) {
				key = tablePrimaryKeys.getString("COLUMN_NAME");
				for (int j = tableStartPos.get(tableName); j <= tableEndPos
						.get(tableName); j++) {
					currentTableName = rsmd.getColumnName(j);
					if (key.equalsIgnoreCase(currentTableName)) {
						if (this.pkPerColumn.containsKey(i)) {
							keys = this.pkPerColumn.get(i);
							keys.add(j);
							this.pkPerColumn.put(i, keys);
						} else {
							this.pkPerColumn.put(i, new LinkedList<Integer>());
							keys = this.pkPerColumn.get(i);
							keys.add(j);
							this.pkPerColumn.put(i, keys);
						}
					}
				}
			}
		}
		tablePrimaryKeys.close();
	}
	
	/*
	* Get Foreign Key Position in the result set
	* this method set the value of foreignKeyColumn to contains the column
	* that is Foreign Key AND equals the Document Root
	*/
	private void resultSetFKPos() throws SQLException {
		ResultSet tableFK = null;
		this.foreignKeyColumn = new HashSet<>();
		
		for (int col = 1; col <= columnCount; col++) {
			tableName = tableNames.get(col);
			tableFK = meta.getImportedKeys(null, null, tableName);
			while (tableFK.next()) {
				
				// equal the DocRoot's tablename
				if (tableFK.getString("PKTABLE_NAME").equals(tableNames.get(1))	&&
						// equal the current column name, because of JDBC's drawback
						tableFK.getString("FKCOLUMN_NAME").equals(rsmd.getColumnName(col)) && 
						// equal the DocRoot
						tableFK.getString("FKCOLUMN_NAME").equals(rsmd.getColumnName(1))) {
					this.foreignKeyColumn.add(col);
					col = tableEndPos.get(tableName);
				}
				
			}
		}
		tableFK.close();
	}

	/*
	 * Mapping SQL data into Java Types
	 * See : http://docs.oracle.com/javase/6/docs/technotes/guides/jdbc/getstart/mapping.html
	 * The returned value type is Object, to make it possible to store in Collections
	 * The value later will be written into JSON using writeHelper method in WriteJSON
	 */
	public Object getObjectFromResultSet(int column) throws SQLException {
		Object o = null;
		switch (rsmd.getColumnType(column)) {

		case java.sql.Types.BIGINT:
			o = rs.getLong(column);
			break;

		case java.sql.Types.INTEGER:
		case java.sql.Types.TINYINT:
		case java.sql.Types.SMALLINT:
			o = rs.getInt(column);
			break;

		case java.sql.Types.BOOLEAN:
		case java.sql.Types.BIT:
			o = rs.getBoolean(column);
			break;

		case java.sql.Types.REAL:
			o = rs.getFloat(column);
			break;

		case java.sql.Types.DECIMAL:
		case java.sql.Types.NUMERIC:
			o = rs.getBigDecimal(column);
			break;

		case java.sql.Types.DOUBLE:
		case java.sql.Types.FLOAT:
			o = rs.getDouble(column);
			break;

		case java.sql.Types.CHAR:
		case java.sql.Types.VARCHAR:
		case java.sql.Types.LONGVARCHAR:
		case java.sql.Types.NVARCHAR:
			// This is suitable for retrieving normal data, but can be unwieldy
			// if the JDBC type LONGVARCHAR is being used to store
			// multi-megabyte strings
			o = rs.getString(column);
			break;

		case java.sql.Types.BINARY:
		case java.sql.Types.VARBINARY:
		case java.sql.Types.LONGVARBINARY:
			// we're being general here. According to Oracle recommendation, it
			// should be returned as byte[]
			o = rs.getString(column);
			break;

		case java.sql.Types.DATE:
			o = rs.getDate(column);
			break;

		case java.sql.Types.TIMESTAMP:
			o = rs.getTimestamp(column);
			break;

		case java.sql.Types.TIME:
			o = rs.getTime(column);
			break;

		case java.sql.Types.ARRAY:
		case java.sql.Types.BLOB:
		case java.sql.Types.DISTINCT:
		case java.sql.Types.CLOB:
		case java.sql.Types.STRUCT:
		case java.sql.Types.REF:
		case java.sql.Types.JAVA_OBJECT:
			// these data types are custom, unique, and rarely used
			// Don't know what to do. Ignore it for now
			break;

		default:
			o = rs.getString(column);
			break;
		}

		return o;
	}

	public ResultSet executeQuery() throws SQLException {
		stmt = c.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY,
				java.sql.ResultSet.CONCUR_READ_ONLY);
		stmt.setFetchSize(Integer.MIN_VALUE);
		rs = this.stmt.executeQuery(QUERY);
		return rs;
	}
}
