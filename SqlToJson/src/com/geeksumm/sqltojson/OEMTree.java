package com.geeksumm.sqltojson;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.BlockingQueue;

public class OEMTree extends Thread {
	private DBHelper database;
	private ResultSet rs;
	private ResultSetMetaData rsmd;
	private int numCollumns;
	private Map<Integer, Object> lastRow;
	private String columnName, document_id;
	private Object obj;
	private boolean firstRun;
	private Set<String> docRootSet;
	private Map<Integer, List<Object>> tree;
	private BlockingQueue<Map<Integer, List<Object>>> writeQueue;

	public OEMTree(DBHelper database,
			BlockingQueue<Map<Integer, List<Object>>> writeQueue) {
		this.writeQueue = writeQueue;
		this.database = database;
		firstRun = true;
	}

	public boolean isDifferent(Object last_object, Object new_object) {
		try {
			return !last_object.equals(new_object);
		} catch (NullPointerException nl) {
			//this exception is raised when:
			//null.equals(null);
			//null.equals(object);
			//object.equals(null);
			return true;
		}
	}

	private void buildTree() throws SQLException, Exception {
		for (int currColumn = 1; currColumn <= numCollumns; currColumn++) {			
			// if this column is the same as DocRoot AND are Foreign Keys, then ignore it
			if (database.getForeignKeyColumn().contains(currColumn)) {
				tree.put(currColumn, null);
				continue;
			}
			
			columnName = rsmd.getColumnLabel(currColumn);
			
			// to make sure you adhere to the constructs that the first collumn ARE DocRoot
			if (currColumn == 1
					&& columnName.equals(SqlToJson.config.getDocRoot())) {
				document_id = rs.getString(currColumn);
				
				// create new oemTree if the DocumentRoot's are unique
				if (docRootSet.add(document_id)) {
					if (!tree.isEmpty()) {
						writeQueue.put(tree);
					}
					tree = new LinkedHashMap<Integer, List<Object>>();
				}
				
			} else if (currColumn == 1
					&& !columnName.equals(SqlToJson.config.getDocRoot())) {
				throw new Exception(
						"Exception, the DocumentRoot should be the first collumn! Modify your query or config file!");
			}
			
			if (!tree.containsKey(currColumn)) {
				tree.put(currColumn, new LinkedList<Object>());
			}

			obj = database.getObjectFromResultSet(currColumn);
			lastRow.put(currColumn, obj);			
		}// end iterating one row

		checkLastRow(tree, lastRow);
		lastRow = new HashMap<Integer, Object>();
	}

	/*
	 * Add current branch to tree, and perform some checking to make sure the
	 * value is unique
	 * the value that is compared are the value of the column which are PK of
	 * the currently processed column if there's minimum one value where value-PK 
	 * is different, then the currently processed column is unique
	 */
	private void checkLastRow(Map<Integer, List<Object>> tree,
			Map<Integer, Object> lastRow) throws SQLException {
		Map<Integer, Object> lastInsertedRow = new HashMap<Integer, Object>();
		List<Integer> relatedPK;
		List<Object> leafOEM = Collections.emptyList();
		Boolean unique;

		// Retrieved the lastInsertedRow from OEM TREE
		for (Map.Entry<Integer, List<Object>> entry : tree.entrySet()) {
			try {
				if (entry.getValue().size() != 0) {
					lastInsertedRow.put(entry.getKey(),
							entry.getValue().get(entry.getValue().size() - 1));
				} else {
					lastInsertedRow.put(entry.getKey(), null);
				}
			} catch (NullPointerException nl) {
				// This branch doesn't have value..
				// we ignore it because this branch is the same as the first branch / docRoot (column 1 in the result set)
				continue;
			}

		}

		// compare each PK collumn for uniqueness before inserted
		for (Map.Entry<Integer, List<Object>> entry : tree.entrySet()) {
			leafOEM = entry.getValue();
			if (leafOEM == null) {
				continue;
			}
			relatedPK = database.getPkPerCollumn().get(entry.getKey());
			unique = false;
			for (Integer collumn : relatedPK) {
				if (database.getForeignKeyColumn().contains(collumn)) {
					continue;
				}
				if (firstRun) {
					// always insert the first row
					unique = true;
				} else {
					// are concerned PK of the collumn is unique?
					unique = unique
							|| isDifferent(lastInsertedRow.get(collumn),
									lastRow.get(collumn));
				}
			}
			if (unique) {
				leafOEM.add(lastRow.get(entry.getKey()));
				tree.put(entry.getKey(), leafOEM);
			}
		}
		this.firstRun = false;
	}

	public void build() {
		try {
			rs = database.executeQuery();
			rsmd = rs.getMetaData();
			numCollumns = rsmd.getColumnCount();
			lastRow = new HashMap<Integer, Object>();			
			docRootSet = new HashSet<String>();
			tree = Collections.emptyMap();
		} catch (SQLException e) {
			System.out.println("SQl Exception");
			e.printStackTrace();
		}
		start();
	}

	public void run() {
		try {
			while (rs.next()) {
				try {
					buildTree();
				} catch (Exception e) {
					System.out.println("Please modify your query or DocumentRoot so that both of them are the same");
					System.out.println("Sql table result conversion to OEM Tree failed. Exit..");
					e.printStackTrace();
					System.exit(1);
				}
			}
			// don't forget to add the last generated tree to the queue
			if (!tree.isEmpty()) {
				writeQueue.put(tree);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (InterruptedException i) {
			i.printStackTrace();
		}
	}
}
