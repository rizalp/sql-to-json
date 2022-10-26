package com.geeksumm.sqltojson;
/**
 * 
 */

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mohammad Shahrizal Prabowo
 *
 */
public class SqlToJson {

	private static BlockingQueue<Map<Integer, List<Object>>> writeQueue;
	static Config config;
	private static ObjectMapper reader;	
	static long startTime, endTime, totalTime;
	/**
	 * @param args
	 */	
	public static void main(String[] args) {
		System.out.println("Start mapping....");
		startTime = System.currentTimeMillis();		
		writeQueue = new LinkedBlockingQueue<>();
		reader = new ObjectMapper();
		DBHelper database = null;
		
		try {
			config = reader.readValue(ClassLoader.getSystemClassLoader().getResourceAsStream("json/config.json"), Config.class);
			database = new DBHelper(config);
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (JsonParseException e) {
			System.out.println("Parsing JSON configuration Error.");
			System.exit(1);
			e.printStackTrace();
		} catch (JsonMappingException e) {
			System.out.println("Mapping JSON configuration Error.");
			System.exit(1);
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("File Not Found. Exiting..");
			System.exit(1);
			e.printStackTrace();
		}
		
		OEMTree oem = new OEMTree(database, writeQueue);
		WriteJSON writer;
		try {
			writer = new WriteJSON(oem, writeQueue, database.getRsmd(), config);
			oem.build(); //Producer. Produce OEM Tree, and put it into the queue
			writer.write(); //Consumer. Consume OEM Tree from the queue, and write it into the JSON
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
