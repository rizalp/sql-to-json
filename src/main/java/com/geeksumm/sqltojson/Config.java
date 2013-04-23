package com.geeksumm.sqltojson;

/*
 * Class wrapper for configuration file config.json
 */

import java.util.*;

public class Config {
	public enum JDBCDriver {
		mysql, postgresql, sqlserver, oracle, mariadb
	}

	public static class SubNode {
		private int _startColumn, _endColumn;
		private String _name;

		/*will be implemented in future release*/
//		private List<SubNode> _subnodes;
//
//		public void setSubNodes(List<SubNode> s){
//			this._subnodes = s;
//		}
//
//		public List<SubNode> getSubNodes() {
//			return this._subnodes;
//		}

		public int getStartColumn() {
			return _startColumn;
		}

		public int getEndColumn() {
			return _endColumn;
		}

		public String getName() {
			return _name;
		}

		public void setStartColumn(int i) {
			_startColumn = i;
		}

		public void setEndColumn(int i) {
			_endColumn = i;
		}

		public void setName(String s) {
			_name = s;
		}
	}

	private JDBCDriver _jdbcDriver;
	private boolean _isPrettyPrint;
	private String _query;
	private String _docRoot;
	//	private int _split;
	private String _host;
	private String _database;
	private String _user;
	private String _password;
	private List<SubNode> _subnode;

	public void setSubNode(List<SubNode> s) {
		this._subnode = s;
	}

	public List<SubNode> getSubNode() {
		return this._subnode;
	}

	public JDBCDriver getJdbcDriver() {
		return _jdbcDriver;
	}

	public boolean isPrettyPrint() {
		return _isPrettyPrint;
	}

	public String getQuery() {
		return _query;
	}

	public String getDocRoot() {
		return _docRoot;
	}

	public void setJdbcDriver(JDBCDriver _jdbcDriver) {
		this._jdbcDriver = _jdbcDriver;
	}

	public void setPrettyPrint(boolean _prettyPrint) {
		this._isPrettyPrint = _prettyPrint;
	}

	public void setQuery(String _query) {
		this._query = _query;
	}

	public void setDocRoot(String _docRoot) {
		this._docRoot = _docRoot;
	}

	public String getHost() {
		return _host;
	}

	public String getDatabase() {
		return _database;
	}

	public String getUser() {
		return _user;
	}

	public String getPassword() {
		return _password;
	}

	public void setHost(String _host) {
		this._host = _host;
	}

	public void setDatabase(String _database) {
		this._database = _database;
	}

	public void setUser(String _user) {
		this._user = _user;
	}

	public void setPassword(String _password) {
		this._password = _password;
	}
}
