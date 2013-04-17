# SqlToJson
#### Mapping SQL Query result into JSON

This is command line tools to convert SQL Query result into JSON file. Implemented using JDBC API. Currently only support MySQL, altough other DBMS is planned soon.

## Installation
1. Git clone into the classpath
2. edit config.json. Read config.json.sample for available options
3. Enjoy!

## Examples
employees sample database schema

## Background Theory
1. The returned result from executed query is modelled using Object Exchange model (OEM). Read this for more info for the underlying model
2. The OEM will implemented using Map data structure, with column number as its keys, and List of Objects as its value
3. Mapping SQl Types into Java Types is done by following [Oracle guidelines](http://docs.oracle.com/javase/6/docs/technotes/guides/jdbc/getstart/mapping.html) 
4. JSON Parser and Generator are provided by [Jackson](http://wiki.fasterxml.com/JacksonHome)

## Important 
* if you write query that joined tables, you should modify subnodes
* beware of the same collumn name between different table!. This program will not append anything into the field name!
* if you use alias, make sure the first column's alias is the same as docRoot
* you should use alias if you joined table
* This tools works on the assumption that posisi kolomnya beurutan sesuai dengan tabel
* If your result contain multiple occurance of DocRoot (perhaps as FK), this tool is clever enough to ignore it.


## Limitations
duplicate fields... JSON is allowing it, but the implemented value will be the last one. this probably isn't what you want
example : "emp_no" : 110386
data types limitations

## Implementation Notes
Since it's possible that results set is quite large, and the OEM Tree can't be fitted all in the memory, we'll be using Producer - Consumer Design Pattern approach. The producer is OEMTree object, that will read the ResultSet, build the tree, and put it into the queue. The consumer is WriteJSON object that will retrieve the value from queue, perform necessary cleaning, and write it into the JSON file. 

The queue implementation is LinkedBlockingQueue from [java.util.concurrent](link), which is recommended for this type of problems. If the queue is empty, the consumer will wait 400ms before consuming the queue again.

You know from the samples that Joined Table have a lot of duplicate values. How did this tools map it up into clean, self-contained JSON ?
1. Get Primary key for each collumn, and find its location in the ResultSet
2. Since Joined table have alot of duplicate / null value, we need to add a bit more cleaning before adding it to the branch. We'll use each collumn PK information to define wheter this cell's data is unique or not.
3. Finally, before writting OEM into JSON, we'll delete the duplicate by finding the minimum occurance of duplicate value in the branch. Then, cut the branch using sublist(0, branch.size()/duplicates)


## Versioning
#### Latest Release v0.1

* Currently only support one level of nested nodes
*
*

## To Do

* More output stream choices
* Other DBMS support
* Deeper Nested Nodes


## License 

Copyright 2013 Mohammad Shahrizal Prabowo

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
