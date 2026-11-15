FROM tomcat:11.0-jdk21-temurin

# SQL Server JDBC driver, shared by all webapps via Tomcat's lib folder.
COPY lib/mssql-jdbc-13.6.0.jre11.jar /usr/local/tomcat/lib/
