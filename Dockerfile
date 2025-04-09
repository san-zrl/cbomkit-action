FROM openjdk:21

COPY --chown=0:0 CBOMkit-action.jar /CBOMkit-action.jar
COPY --chown=0:0 src/main/resources/java/scan/*.jar /java/scan/

ENV CBOMKIT_JAVA_JAR_DIR="/java/scan/"

CMD ["java","-Xmx16G","-jar","/CBOMkit-action.jar"]
