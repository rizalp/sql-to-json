package com.geeksumm.sqltojson;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.BlockingQueue;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.geeksumm.sqltojson.Config.SubNode;

public class WriteJSON extends Thread {
	private OEMTree oem;
	private Map<Integer, List<Object>> tree;
	private BlockingQueue<Map<Integer, List<Object>>> writeQueue;
	private ResultSetMetaData rsmd;
	private Config config;
	private static JsonFactory jsonFactory;
	private static JsonGenerator jsonGenerator;

	public WriteJSON(OEMTree oem,
			BlockingQueue<Map<Integer, List<Object>>> writeQueue,
			ResultSetMetaData rsmd, Config config) throws IOException {
		this.writeQueue = writeQueue;
		this.oem = oem;
		this.rsmd = rsmd;
		this.config = config;
	}
	private void flushTree() throws SQLException, IOException {
		jsonGenerator.writeStartObject();
		try {
			if (config.getSubNode().isEmpty()) {
				writeFlat();
			} else {
				writeNested(config.getSubNode());
			}
		} catch (NullPointerException nl) {
			// this means that the subnode field in config.json is missing
			// assume that the subnode is empty, and write as flat
			writeFlat();
		}
		jsonGenerator.writeEndObject();
	}

	private Map<Integer, SubNode> nodeCollumn(List<SubNode> subNode) {
		Map<Integer, SubNode> mn = new HashMap<>();
		for (SubNode node : subNode) {
			mn.put(node.getStartColumn(), node);
		}
		return mn;
	}
	
	private boolean isAllNulls(List<Object> branch){
		boolean onlyNull = true;
			for (Object object : branch) {
				if (object != null) {
					onlyNull = false;
					break;
				}
			}
		return onlyNull;
	}
	
	private void writeNested(List<SubNode> subNode) throws SQLException,
			JsonGenerationException, IOException {
		int numCollumns = rsmd.getColumnCount();
		Map<Integer, SubNode> mapNodes = nodeCollumn(subNode);
		List<Object> branch;

		for (int i = 1; i <= numCollumns; i++) {
			branch = tree.get(i);
			if (branch == null) {
				continue; 
			}

			if (mapNodes.containsKey(i)) {
				SubNode node = mapNodes.get(i);
				Boolean isSubNodeContainElement = false;
				
				for (int bol = node.getStartColumn(); bol <= node
						.getEndColumn(); bol++) {
					branch = tree.get(bol);
					if (branch == null) {
						continue; 
					}					
					isSubNodeContainElement = isSubNodeContainElement
							|| (!branch.isEmpty() && !isAllNulls(branch));
				}
				
				if (isSubNodeContainElement) {
					jsonGenerator.writeArrayFieldStart(node.getName());
					writeSubNode(node.getStartColumn(), node.getEndColumn());
					jsonGenerator.writeEndArray();
				}
				i = node.getEndColumn();
			} else {
				writeAsArray(i, branch);
			}
		}
	}

	private void writeSubNode(int startColumn, int endColumn)
			throws JsonGenerationException, IOException, SQLException {
		Map<Integer, List<Object>> subTree = new LinkedHashMap<>();
		List<Object> branch;
		int objCount = 0;

		for (int j = startColumn; j <= endColumn; j++) {
			try {
				branch = condensedBranch(tree.get(j));
				objCount = Math.max(objCount, branch.size());
				subTree.put(j, branch);				
			} catch (NullPointerException nl) {
				// Ignore it.
			}
		}
		for (int i = 0; i < objCount; i++) {
			jsonGenerator.writeStartObject();
			for (Map.Entry<Integer, List<Object>> entry : subTree.entrySet()) {
				try {
					writeHelper(rsmd.getColumnLabel(entry.getKey()), entry
							.getValue().get(i));
				} catch (IndexOutOfBoundsException iex) {
					// That means this column don't have value for specific
					// Row. We just ignore it since semistructured model allows
					// us to ignore it.
				}
			}
			jsonGenerator.writeEndObject();
		}
	}

	private void writeAsArray(int column, List<Object> branch)
			throws SQLException {
		branch = condensedBranch(branch);
		String column_name = rsmd.getColumnLabel(column);
		try {
			if (branch.size() > 1) {
				jsonGenerator.writeArrayFieldStart(column_name);
				for (Object object : branch) {
					writeHelper(object);
				}
				jsonGenerator.writeEndArray();
			} else if (branch.size() == 1) {
				for (Object object : branch) {
					writeHelper(column_name, object);
				}
			}
		} catch (IOException io) {
			System.out.println(io.getMessage());
			System.exit(1);
		} catch (NullPointerException nl) {
			//ignore it.
		}
	}

	// remove duplicate value from branch
	private List<Object> condensedBranch(List<Object> branch) {
		if (branch == null) {
			//ignore this branch
			return null;
		} else if (isAllNulls(branch)) {
			//ignore this too
			return null;
		}
		int lastElement = branch.size() - 1;
		Set<Integer> indexTocheck = new HashSet<>();
		Map<Integer, Integer> indexDuplicates = new HashMap<>();
		indexTocheck.add(0); // first element
		indexTocheck.add(lastElement); // lastelement;
		Integer duplicates = 0, indx;
		Object toCheck = null;

		if (branch.size() != 1 && !branch.isEmpty()) {
			if (branch.size() == 2
					&& !oem.isDifferent(branch.get(0), branch.get(lastElement))) {
				// [a, a] return [a, a]
				return branch;
			} else if (branch.size() == 2
					&& oem.isDifferent(branch.get(0), branch.get(lastElement))) {
				// [a, b] return it without modification
				return branch;
			} else {
				for (Integer index : indexTocheck) {
					toCheck = branch.get(index);
					
					// check against all the List's Element
					for (Object o : branch) {
						if (!oem.isDifferent(toCheck, o)) { // duplicates
							if (indexDuplicates.containsKey(index)) {
								duplicates = indexDuplicates.get(index);
								duplicates++;
								indexDuplicates.put(index, duplicates);
							} else {
								indexDuplicates.put(index, 1);
							}
						}
					}
				}

				// get the Maximum duplicates value.
				duplicates = Math.max(indexDuplicates.get(0),
						indexDuplicates.get(branch.size() - 1));

				Random rnd = new Random();
				for (int i = 0; i < duplicates; i++) {
					indx = rnd.nextInt(branch.size());
					while (indx.equals(0) || indx.equals(lastElement)) {
						indx = rnd.nextInt(branch.size());
					}
					indexTocheck.add(indx);
				}

				for (Integer index : indexTocheck) {
					if (!index.equals(0) && !index.equals(branch.size() - 1)) {
						toCheck = branch.get(index);
						for (Object o : branch) { // check against all the
													// List's
													// Element
							if (!oem.isDifferent(toCheck, o)) { // duplicates
								if (indexDuplicates.containsKey(index)) {
									duplicates = indexDuplicates.get(index);
									duplicates++;
									indexDuplicates.put(index, duplicates);
								} else {
									indexDuplicates.put(index, 1);
								}
							}
						}
					}
				}
				duplicates = indexDuplicates.get(0);
				for (Integer index : indexTocheck) {
					duplicates = Math.min(duplicates,
							indexDuplicates.get(index));
				}
				List<Object> subBranch = branch.subList(0, branch.size()
						/ duplicates);
				return subBranch;
			}
		} else {
			return branch;
		}
	}

	private void writeFlat() throws SQLException{
		int numCollumns = rsmd.getColumnCount();
		List<Object> branch;		
		for (int i = 1; i <= numCollumns; i++) {
			branch = tree.get(i);
			if (branch == null) {
				continue; 
			}
			writeAsArray(i, branch);
		}
	}

	private void writeHelper(String column_name, Object object)
			throws JsonGenerationException, IOException {
		writeHelper(column_name, object, true);
	}
	private void writeHelper(Object object) throws JsonGenerationException,
			IOException {
		writeHelper(null, object, false);
	}
	private void writeHelper(String column_name, Object object,
			boolean withField) throws JsonGenerationException, IOException {

		if (withField) {
			jsonGenerator.writeFieldName(column_name);
		}

		if (object instanceof String) {
			jsonGenerator.writeString((String) object);

		} else if (object instanceof Long) {
			jsonGenerator.writeNumber((Long) object);

		} else if (object instanceof Integer) {
			jsonGenerator.writeNumber((Integer) object);

		} else if (object instanceof Boolean) {
			jsonGenerator.writeBoolean((Boolean) object);

		} else if (object instanceof Float) {
			jsonGenerator.writeNumber((Float) object);

		} else if (object instanceof BigDecimal) {
			jsonGenerator.writeNumber((BigDecimal) object);

		} else if (object instanceof Double) {
			jsonGenerator.writeNumber((Double) object);

		} else { // default, also used for DATE, TIMESTAMP, and TIME
			jsonGenerator.writeString(object.toString());
		}
	}

	public void write() throws JsonGenerationException, IOException {
		jsonFactory = new JsonFactory();
		jsonGenerator = jsonFactory.createJsonGenerator(
				new File("results.json"), JsonEncoding.UTF8);

		if (SqlToJson.config.isPrettyPrint()) {
			jsonGenerator.useDefaultPrettyPrinter();
		}
		jsonGenerator.writeStartObject();
		jsonGenerator.writeArrayFieldStart("results");
		start();
	}

	public void run() {
		while (oem.isAlive()) {
			try {
				tree = writeQueue.take();
				flushTree();
				synchronized (this) {
					if(writeQueue.isEmpty()){
						this.wait(400);//wait 400ms if queue is empty
					}
				}
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException io) {
				io.printStackTrace();
			} catch (SQLException sql) {
				sql.printStackTrace();
			}
		}

		try {
			jsonGenerator.writeEndArray();
			jsonGenerator.writeEndObject();
			jsonGenerator.close();
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		SqlToJson.endTime = System.currentTimeMillis();
		SqlToJson.totalTime = SqlToJson.endTime - SqlToJson.startTime;
		System.out.println("Mapping successful. Time: " + SqlToJson.totalTime + " ms");
	}
}
