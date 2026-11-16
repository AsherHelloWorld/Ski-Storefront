FROM tomcat:11.0-jdk21-temurin

# SQL Server JDBC driver, shared by all webapps via Tomcat's lib folder.
COPY lib/mssql-jdbc-13.6.0.jre11.jar /usr/local/tomcat/lib/

# JaCoCo coverage agent (dev only). docker-compose.yml switches it on via CATALINA_OPTS;
# the test runner collects coverage of the compiled JSPs from it over port 6300.
ADD https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar /opt/jacoco/jacocoagent.jar

# Ask Jasper (Tomcat's JSP compiler) to write a .smap file next to each compiled JSP.
# It maps generated-Java lines back to .jsp lines, so coverage is reported per JSP line
# instead of including Tomcat's generated boilerplate.
RUN sed -i 's|<servlet-class>org.apache.jasper.servlet.JspServlet</servlet-class>|&<init-param><param-name>dumpSmap</param-name><param-value>true</param-value></init-param>|' \
        /usr/local/tomcat/conf/web.xml \
 && grep -q dumpSmap /usr/local/tomcat/conf/web.xml
