Appening
========


Components
----------

Appening consists of the following components:

* A backend with a MySQL database and a Java process for the collection of Twitter messages, trend calculation, and data generation for the frontend
* A frontend made out of HTML/JS hosted in S3 for scalability.

Custom Setup
------------

### Prerequisites
Install Java, Maven, s3cmd and MySQL

### Local Coordinates

Determine GPS coordinates for both a bounding box and circle for your area, e.g. for Amsterdam:
* Box top left corner: 52.457,4.712 / bottom right corner: 52.271,5.042 (for OSM extraction)
* Circle center: 52.3741,4.897 / Radius = 10km (for Twitter API)

### Database Creation
* Create a new database in MySQL
* Run the SQL file /src/main/resources/appening.sql to create tables, stored procedures and events
* Enable the MySQL event scheduler 
	
	`SET GLOBAL event_scheduler = ON;`

### Java Backend code
* Clone the GIT repository
* Copy the file /src/main/resources/appening.properties.dist to /src/main/resources/appening.properties
* Edit appening.properties to your environment, e.g. Database credentials, GPS geometry, AWS and S3 access, Twitter Credentials
* Create Appening JAR 
	
	`mvn install`

### List of Places
Populate the places table, e.g. from OpenStreetMap dumps (http://download.geofabrik.de/openstreetmap/)
* Use the osmosis tool to create a OSM file for your area, substitute coordinates
	
	`gzcat some-osm-dump.osm.gz| ./osmosis --read-xml file=- --bb top=52.457 left=4.712 bottom=52.271 right=5.042 --write-xml file=- | gzip > smaller.osm.gz`

* use OsmTools from the Appening repo to extract named places from the OSM file and save to DB

### Upload frontend to S3
* Adjust /src/main/frontend/index.html to your environment, several places (Hint: replace "Amsterdam" with your cities' name)
* Create a S3 bucket with the same name as your domain, e.g. www.appening.at
* Upload frontend to S3,  e.g. 
	
	`s3cmd -P --exclude "data/*" sync . s3://your.bucket.name/`             

### Start the tweet collection
Run the Java part of the backend:
	`java -cp appening.jar -Dfile.encoding=UTF-8 org.dentleisen.appening2.AppeningBackend`

### Check if everything is running
* messages table starts getting tweets
* after a while (could be several hours), entries start appearing in counts/nowcounts table
* JSON is being uploaded to S3 (/data/ prefix)



