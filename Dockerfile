FROM openjdk:17

COPY --chown=0:0 CBOMkit-action.jar /CBOMkit-action.jar
COPY --chown=0:0 src/main/resources/java/scan/*.jar /java/scan/

ENV CBOMKIT_JAVA_JAR_DIR="/java/scan/"

CMD ["java","-jar","/CBOMkit-action.jar"]