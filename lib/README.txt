Place the SQLite JDBC driver in this folder.

1. Go to: https://github.com/xerial/sqlite-jdbc/releases
2. Download the latest jar, e.g. sqlite-jdbc-3.46.0.0.jar
3. Copy that .jar file into this lib/ folder.

The run scripts (run.sh / run.bat) automatically add every .jar in this
folder to the classpath, so no further configuration is needed.

The app uses only the standard java.sql API, so any recent sqlite-jdbc
release will work.
